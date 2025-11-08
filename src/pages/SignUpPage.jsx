import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { registerUser } from "../services/user-service";
import "../styles.css";

export default function SignUpPage() {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();

    try {
      const data = await registerUser({ username, email, password });

      // ✅ Save user & token (auto login after signup)
      localStorage.setItem("token", data.token);
      localStorage.setItem("username", data.username);

      alert("Account created successfully 🎉");
      navigate("/"); // redirect to homepage
    } catch (err) {
      console.log(err);

      // ✅ Handle duplicate email
      if (err.response && err.response.data?.message?.includes("Email already exists")) {
        alert("Email already in use. Try another.");
      } else {
        alert("Signup failed. Try again.");
      }
    }
  };

  return (
    <div className="login-container">
      <h1 className="login-title">Create Account 🚀</h1>

      <form className="login-form" onSubmit={handleSubmit}>

        {/* Username */}
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

        {/* Email */}
        <div className="input-group">
          <label>Email</label>
          <input 
            type="email"
            value={email}
            onChange={(e)=>setEmail(e.target.value)}
            placeholder="Enter email"
            required
          />
        </div>

        {/* Password */}
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

        <button className="login-btn" type="submit">Sign Up</button>

        <p className="signup-text">
          Already have an account?{" "}
          <Link className="signup-link" to="/login">Login</Link>
        </p>
      </form>
    </div>
  );
}
