import React from "react";
import { Link, useNavigate } from "react-router-dom";

export default function Navbar() {
  const navigate = useNavigate();
  const username = localStorage.getItem("username");

  const handleLogout = () => {
    localStorage.clear();
    navigate("/login");
  };

  return (
    <header className="navbar">
      <div className="nav-inner">
        <div className="brand">
          <img src="/assets/BeerGameLogo.png" alt="Beer Game Logo" className="logo" />
          <span>Beer Game</span>
        </div>

        <nav className="nav-links">
          <a href="#play">Play</a>
          <a href="#learn">Learn</a>
          <a href="#about">About</a>
          <a href="#contact">Contact</a>

          {username ? (
            <>
              <span style={{ marginRight: "10px" }}>Hi, {username}</span>
              <button className="btn secondary" onClick={handleLogout}>
                Logout
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="btn secondary">Login</Link>
              <Link to="/sign" className="btn primary">Sign Up</Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
