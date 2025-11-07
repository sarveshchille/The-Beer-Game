import React from "react";

const reviews = [
  { name: "André Scholl", text: "Complete statistics and appealing visualizations allow discussion and reflection." },
  { name: "Lutz Heldmaier", text: "A good eye-opener to show the bullwhip-effect." }
];

export default function Testimonials() {
  return (
    <section className="testimonials container">
      <h2>See What People Are Saying about Us</h2>
      <div className="review-list">
        {reviews.map((r, i) => (
          <blockquote key={i} className="review">
            <p>"{r.text}"</p>
            <footer>- {r.name}</footer>
          </blockquote>
        ))}
      </div>
    </section>
  );
}
