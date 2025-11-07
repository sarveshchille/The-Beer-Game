import React from "react";


export default function Hero() {
  return (
    <section className="hero container">
      <div className="hero-left">
        <h1>Play the Beer Game — a playful way to master supply chain dynamics</h1>
        <p>
          Experience the classic Beer Distribution Game digitally — perfect for teams and classrooms.
          Create or join games instantly, no account needed.
        </p>

      

        <div className="metrics">
          <div><strong>39K+</strong> Games played</div>
          <div><strong>1000+</strong> Institutions</div>
          <div><strong>11+</strong> Years of impact</div>
        </div>
      </div>

      <div className="hero-right">
        <div className="hero-card">
        <img src="/assets/beer-illustration.jpg" alt="Beer game preview" />
        </div>
      </div>
    </section>
  );
}
