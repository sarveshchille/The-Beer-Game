import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from "../context/authContext";

import "../styles.css";
export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    login(username);
    navigate('/');
  };

  return (
    <div className="login-container">
      <h1 className="login-title">Welcome Back 👋</h1>

      <form className="login-form" onSubmit={handleSubmit}>
        <div className="input-group">
          <label>Username</label>
          <input 
            type="text"
            value={username}
            onChange={(e)=>setUsername(e.target.value)}
            placeholder="Enter username"
            required
          />
        </div>

        <div className="input-group">
          <label>Password</label>
          <input 
            type="password"
            value={password}
            onChange={(e)=>setPassword(e.target.value)}
            placeholder="Enter password"
            required
          />
        </div>

        <button className="login-btn" type="submit">Login</button>

        <p className="signup-text">
          Don't have an account? <Link className="signup-link" to="/sign">Sign up</Link>
        </p>
      </form>
    </div>
  );
}
