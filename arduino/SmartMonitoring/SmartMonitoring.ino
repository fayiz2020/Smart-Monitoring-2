/* Smart Monitoring - Arduino UNO + A7670C GSM (SMS control)
   Relays: D4..D13 | GSM: D2 (RX <- module TX), D3 (TX -> module RX via divider)
   Monitoring: A0..A5 | Commands by SMS: "<PIN> <command>"            */
#include <SoftwareSerial.h>
#include <EEPROM.h>
#include <ctype.h>

#define RELAY_ACTIVE_LOW 1   // 1 = most relay boards (LOW turns relay ON)
SoftwareSerial gsm(2, 3);

struct Chan { uint8_t en, mode, dir, cut; float lo, hi, sc, off; uint32_t dly; };
struct Cfg  { uint8_t magic; char pin[9]; char num[20]; uint8_t atype; Chan ch[6]; };
Cfg cfg;
bool rs[14];
uint32_t lastAl[6], tPoll, tMon;
char rxb[96];

void saveCfg() { EEPROM.put(0, cfg); }
void loadCfg() {
  EEPROM.get(0, cfg);
  if (cfg.magic != 0xA6) {
    memset(&cfg, 0, sizeof(cfg)); cfg.magic = 0xA6; strcpy(cfg.pin, "1234");
    for (uint8_t i = 0; i < 6; i++) { cfg.ch[i].sc = 1; cfg.ch[i].mode = 1; cfg.ch[i].hi = 100; }
    saveCfg();
  }
}
void setRelay(uint8_t p, bool on) { rs[p] = on; digitalWrite(p, (on ^ RELAY_ACTIVE_LOW) ? HIGH : LOW); }

bool readLine(char *b, uint8_t n, uint16_t to) {
  uint8_t i = 0; uint32_t t = millis();
  while (millis() - t < to) {
    while (gsm.available()) {
      char c = gsm.read();
      if (c == '\n') { b[i] = 0; if (i > 0) return true; i = 0; }
      else if (c != '\r' && i < n - 1) b[i++] = c;
    }
  }
  b[0] = 0; return false;
}
bool waitFor(const char *tok, uint32_t ms) {
  char b[40]; uint8_t i = 0; uint32_t t = millis();
  while (millis() - t < ms) {
    while (gsm.available()) {
      char c = gsm.read();
      if (i >= 39) { memmove(b, b + 20, 19); i = 19; }
      b[i++] = c; b[i] = 0;
      if (strstr(b, tok)) return true;
    }
  }
  return false;
}
bool at(const char *c, uint32_t ms = 2000) { gsm.println(c); return waitFor("OK", ms); }

void sendSms(const char *num, const char *txt) {
  if (!num[0]) return;
  while (gsm.available()) gsm.read();
  gsm.print(F("AT+CMGS=\"")); gsm.print(num); gsm.print(F("\"\r"));
  if (waitFor(">", 5000)) { gsm.print(txt); gsm.write(26); waitFor("OK", 20000); }
  Serial.println(txt);
}
void callNum(const char *num) {
  if (!num[0]) return;
  gsm.print(F("ATD")); gsm.print(num); gsm.print(F(";\r"));
  delay(25000); gsm.println(F("ATH")); waitFor("OK", 2000);
}
void alertMsg(const char *t) {
  if (cfg.atype == 0 || cfg.atype == 2) sendSms(cfg.num, t);
  if (cfg.atype >= 1) callNum(cfg.num);
}

void monitor() {
  for (uint8_t i = 0; i < 6; i++) {
    Chan &c = cfg.ch[i]; if (!c.en) continue;
    uint32_t s = 0; for (uint8_t k = 0; k < 4; k++) s += analogRead(A0 + i);
    float v = (s / 4.0) * c.sc + c.off;
    bool bad = c.dir == 0 ? v > c.hi : c.dir == 1 ? v < c.lo : (v < c.lo || v > c.hi);
    if (!bad) continue;
    bool cut = false;
    if (c.mode == 2 && c.cut >= 4 && c.cut <= 13 && rs[c.cut]) { setRelay(c.cut, false); cut = true; }
    if (cut || lastAl[i] == 0 || millis() - lastAl[i] >= c.dly * 1000UL) {
      char t[80], vb[14]; dtostrf(v, 1, 2, vb);
      snprintf(t, sizeof t, "ALERT A%d=%s%s", i, vb, cut ? " RELAY CUT" : "");
      lastAl[i] = millis() | 1;
      alertMsg(t);
    }
  }
}

void reply(const char *to, const char *t) { sendSms(cfg.num[0] ? cfg.num : to, t); }
const char *nx() { char *t = strtok(NULL, " "); return t ? t : "0"; }

void handle(const char *from, char *txt) {
  char *p = strtok(txt, " ");
  if (!p || strcmp(p, cfg.pin)) return;           // wrong PIN -> ignore
  char *cmd = strtok(NULL, " "); if (!cmd) return;
  char out[120];
  if (isdigit(cmd[0])) {
    int v = atoi(cmd), st = v % 10, pin = 0;
    if (st < 2) {
      if (v >= 2000 && v <= 9091 && (v % 1000) / 10 == v / 1000) pin = v / 1000;
      else if (v >= 1100 && v <= 1131 && (v - 1100) / 10 <= 3) pin = 10 + (v - 1100) / 10;
    }
    if (pin >= 4 && pin <= 13) { setRelay(pin, st); snprintf(out, sizeof out, "P%d %s", pin, st ? "ON" : "OFF"); }
    else strcpy(out, "Bad code");
  } else if (!strcasecmp(cmd, "STAT")) {
    strcpy(out, "R4-13:"); uint8_t n = 6;
    for (uint8_t i = 4; i <= 13; i++) out[n++] = rs[i] ? '1' : '0';
    out[n] = 0;
    for (uint8_t i = 0; i < 6; i++) if (cfg.ch[i].en) {
      char vb[14]; dtostrf(analogRead(A0 + i) * cfg.ch[i].sc + cfg.ch[i].off, 1, 2, vb);
      snprintf(out + strlen(out), 20, " A%d=%s", i, vb);
    }
  } else if (!strcasecmp(cmd, "SET")) {
    uint8_t i = atoi(nx());
    if (i > 5) { strcpy(out, "Bad channel"); }
    else {
      Chan &c = cfg.ch[i];
      c.en = atoi(nx()); c.mode = atoi(nx()); c.dir = atoi(nx());
      c.lo = atof(nx()); c.hi = atof(nx()); c.sc = atof(nx()); c.off = atof(nx());
      c.cut = atoi(nx()); c.dly = atol(nx()); lastAl[i] = 0; saveCfg();
      snprintf(out, sizeof out, "OK SET A%d", i);
    }
  } else if (!strcasecmp(cmd, "NUM")) {
    const char *n = nx(); memset(cfg.num, 0, sizeof cfg.num);
    if (strcmp(n, "0")) strncpy(cfg.num, n, 19);
    cfg.atype = atoi(nx()); saveCfg(); strcpy(out, "OK NUM");
  } else if (!strcasecmp(cmd, "PWD")) {
    memset(cfg.pin, 0, sizeof cfg.pin); strncpy(cfg.pin, nx(), 8); saveCfg(); strcpy(out, "OK PIN");
  } else strcpy(out, "Unknown");
  reply(from, out);
}

void poll() {
  while (gsm.available()) gsm.read();
  gsm.println(F("AT+CMGL=\"REC UNREAD\""));
  char sender[20] = "", body[64] = ""; int idx = -1; bool wantBody = false;
  uint32_t t = millis();
  while (millis() - t < 3000) {
    if (!readLine(rxb, sizeof rxb, 1000)) break;
    if (!strncmp(rxb, "+CMGL:", 6)) {
      if (idx < 0) {
        idx = atoi(rxb + 7); wantBody = true;
        char *q = strchr(rxb, '"');
        if (q) q = strchr(q + 1, '"');
        if (q) q = strchr(q + 1, '"');
        if (q) { q++; char *e = strchr(q, '"'); if (e) { *e = 0; strncpy(sender, q, 19); } }
      }
    } else if (wantBody) { strncpy(body, rxb, 63); wantBody = false; }
    else if (!strcmp(rxb, "OK")) break;
  }
  if (idx >= 0) {
    gsm.print(F("AT+CMGD=")); gsm.println(idx); waitFor("OK", 3000);
    if (body[0]) handle(sender, body);
  }
}

void setup() {
  Serial.begin(9600);
  for (uint8_t p = 4; p <= 13; p++) { setRelay(p, false); pinMode(p, OUTPUT); }  // all OFF at boot
  loadCfg();
  gsm.begin(9600); delay(3000);
  for (uint8_t i = 0; i < 20 && !at("AT"); i++) delay(500);
  at("ATE0"); at("AT+CMGF=1"); at("AT+CSCS=\"GSM\""); at("AT+CMGD=1,4", 5000);
  Serial.println(F("Smart Monitoring ready"));
}
void loop() {
  if (millis() - tPoll > 2000) { tPoll = millis(); poll(); }
  if (millis() - tMon > 1000)  { tMon = millis(); monitor(); }
}
