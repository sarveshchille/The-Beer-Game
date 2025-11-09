import { myAxios } from "./Helper";

// ✅ LOGIN
export const loginUser = (userData) => {
  return myAxios.post("/user/login", userData)
    .then((res) => {
      const { token, username, email } = res.data;

      localStorage.setItem("token", token);
      localStorage.setItem("username", username);
      localStorage.setItem("email", email);

      return res.data;
    });
};

// ✅ SIGNUP (AUTO LOGIN)
export const registerUser = (userData) => {
  return myAxios.post("/user/signup", userData)
    .then((res) => {
      const { token, username, email } = res.data;

      // auto login after signup
      localStorage.setItem("token", token);
      localStorage.setItem("username", username);
      localStorage.setItem("email", email);

      return res.data;
    });
};

// ✅ CREATE LOBBY (Role & Username)
export const createLobby = (data) => {
  return myAxios.post("/lobby/create", data)
    .then((res) => {
      const { role } = data;
      const { roomId } = res.data;

      localStorage.setItem("role", role);
      localStorage.setItem("roomId", roomId);

      return res.data;
    });
};

// ✅ CREATE ROOM
export const createRoom = (data) => {
  return myAxios.post("/room/create", data)
    .then((res) => {
      const { role } = data;
      const { roomId } = res.data;

      localStorage.setItem("role", role);
      localStorage.setItem("roomId", roomId);

      return res.data;
    });
};
