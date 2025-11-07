import React from "react";
import { Link } from "react-router-dom";


export default function Navbar() {
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
          <Link to="/login" className="btn secondary">Login</Link>
          <a href="#create" className="btn primary">Sign Up</a>
        </nav>
      </div>
    </header>
  );
}
