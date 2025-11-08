import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import createroom from '../services/user-service';

export default function CreateRoom() {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [role, setRole] = useState("Retailer");

  useEffect(() => {
    const storedUsername = localStorage.getItem("username");
    const storedEmail = localStorage.getItem("email"); 

    if (!storedUsername || !storedEmail) {
      alert("Please sign up / login first");
      navigate("/login");
      return;
    }

    setUsername(storedUsername);
    setEmail(storedEmail);
  }, [navigate]);

  const handleCreateRoom = async (e) => {
    e.preventDefault();

    try {
      const res = await createroom( { username, email, role });
      const roomId = res.data.roomId;

      alert(`Room created  ID: ${roomId}`);
      navigate(`/game/${roomId}`);
    } catch (err) {
      console.error(err);
      alert("Room creation failed");
    }
  };

  return (
    <div className="login-container">
      <h1>Create Game Room 🎲</h1>

      <form className="login-form" onSubmit={handleCreateRoom}>
        
        <div className="input-group">
          <label>Username</label>
          <input type="text" value={username} disabled />
        </div>

        <div className="input-group">
          <label>Email</label>
          <input type="email" value={email} disabled />
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
          Create Room
        </button>
      </form>
    </div>
  );
}
