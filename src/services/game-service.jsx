import { myAxios } from "./Helper";


export const sendOrder = async (role, qty) => {
  const username = localStorage.getItem("username");
  const roomId = localStorage.getItem("roomId");

  try {
    const res = await myAxios.post("/game/order", {
      username,
      role,
      roomId,
      quantity: qty,
    });

    console.log("✅ Order sent:", res.data);
    return res.data;

  } catch (error) {
    console.error("❌ Order sending failed:", error);
    throw error;
  }
};
export const startGame = async () => {
  const roomId = localStorage.getItem("roomId");

  try {
    const res = await myAxios.post("/game/start", { roomId });
    return res.data;
  } catch (err) {
    console.error("Game start failed:", err);
    throw err;
  }
};