import React from "react";

export default function Gallery() {
  const shots = Array.from({ length: 6 }).map((_, i) => `/src/assets/screenshot-${i+1}.png`);
  return (
    <section className="gallery container">
      <h2>Discover the Game in Action</h2>
      <div className="gallery-grid">
        {shots.map((s, i) => (
          <div key={i} className="shot">
            <img src={s} alt={`screenshot ${i+1}`} />
          </div>
        ))}
      </div>
    </section>
  );
}
