import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import createlobby from '../services/user-service';// your axios file

export default function CreateLobby() {
  const navigate = useNavigate();
  
  const [username, setUsername] = useState("");
  const [role, setRole] = useState("Retailer");

  useEffect(() => {
    const storedUsername = localStorage.getItem("username");
    if (!storedUsername) {
      alert("Please login first");
      navigate("/login");
      return;
    }
    setUsername(storedUsername);
  }, [navigate]);

  const handleCreateLobby = async (e) => {
    e.preventDefault();

    try {
      const res = await createlobby({ username, role });

      const roomId = res.data.roomId; // backend must return roomId

      alert(`Lobby created ✅ ID: ${roomId}`);
      navigate(`/game/${roomId}`);
    } catch (err) {
      console.error(err);
      alert("Failed to create lobby");
    }
  };

  return (
    <div className="login-container">
      <h1>Create Lobby 🎮</h1>

      <form className="login-form" onSubmit={handleCreateLobby}>
        
        <div className="input-group">
          <label>Username</label>
          <input type="text" value={username} disabled />
        </div>

        <div className="input-group">
          <label>Role</label>
          <select value={role} onChange={(e) => setRole(e.target.value)}>
            <option>Retailer</option>
            <option>Wholesaler</option>
            <option>Distributor</option>
            <option>Factory</option>
          </select>
        </div>

        <button className="login-btn" type="submit">
          Create Lobby
        </button>
      </form>
    </div>
  );
}
