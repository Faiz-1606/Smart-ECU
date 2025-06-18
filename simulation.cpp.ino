#include <SoftwareSerial.h>

SoftwareSerial BTSerial(10, 11); // RX, TX
String btBuffer = "";
bool engineOn = false;

void setup() {
  Serial.begin(9600);
  BTSerial.begin(9600);

  while (Serial.available()) Serial.read();
  while (BTSerial.available()) BTSerial.read();

  Serial.println("=== ECU READY ===");
  BTSerial.println("ECU READY");
  delay(500);
  BTSerial.println("OFF");
}

void loop() {
  // Read incoming Bluetooth characters
  while (BTSerial.available()) {
    char c = BTSerial.read();
    if (c == '\n') {
      btBuffer.trim();
      if (btBuffer.length() > 0) {
        Serial.println(">> Command received: " + btBuffer);
        handleCommand(btBuffer);
        btBuffer = ""; // Clear after processing
      }
    } else {
      btBuffer += c;
    }

    // Safety: prevent overflow
    if (btBuffer.length() > 100) {
      btBuffer = "";
    }
  }
}

void handleCommand(String cmd) {
  cmd.toUpperCase();  // Make comparison case-insensitive

  if (cmd.indexOf("STATUS") >= 0) {
    String status = engineOn ? "ON" : "OFF";
    BTSerial.println(status);
    Serial.println("=> Sent status: " + status);

  } else if (cmd.indexOf("IGNITION_ON") >= 0) {
    engineOn = true;
    BTSerial.println("IGNITION_ON");
    Serial.println("=> Ignition turned ON");

  } else if (cmd.indexOf("IGNITION_OFF") >= 0) {
    engineOn = false;
    BTSerial.println("IGNITION_OFF");
    Serial.println("=> Ignition turned OFF");

  } else if (cmd.indexOf("CALL_INCOMING") >= 0) {
    if (engineOn) {
      BTSerial.println("CALL_REJECTED");
      Serial.println("=> Call rejected (engine ON)");
    } else {
      BTSerial.println("CALL_ACCEPTED");
      Serial.println("=> Call accepted (engine OFF)");
    }

  } else {
    BTSerial.println("UNKNOWN_COMMAND");
    Serial.println("=> Unknown command: " + cmd);
  }

  BTSerial.flush();  // Clear the serial buffer
}
