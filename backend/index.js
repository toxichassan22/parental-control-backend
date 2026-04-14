const express = require("express");
const app = express();

app.use(express.json());

// test route
app.get("/", (req, res) => {
  res.send("Server is running 🚀");
});

// main route
app.post("/exchangePairingToken", (req, res) => {
  const { tokenId, deviceName } = req.body;
  
  // TODO: Implement actual pairing logic with Firebase
  // For now, return a mock response for testing
  res.json({
    success: true,
    deviceId: "device_" + Date.now(),
    customToken: "mock_token_" + tokenId,
    message: "paired successfully"
  });
});

// 🔥 أهم سطر
const PORT = process.env.PORT || 3000;

app.listen(PORT, () => {
  console.log("Server running on port " + PORT);
});