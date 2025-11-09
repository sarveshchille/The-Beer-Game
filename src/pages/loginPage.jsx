import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { loginUser } from "../services/user-service";
import "../styles.css";

export default function LoginPage() {
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();

    try {
      const data = await loginUser({ username, password });

      // Save login info
      localStorage.setItem("token", data.token);
      localStorage.setItem("username", data.username);

      navigate('/');
    } catch (err) {
      console.log(err);
      alert("Invalid username or password");
    }
  };

  return (
    <div className="login-container">
      <h1 className="login-title">Welcome Back</h1>

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
