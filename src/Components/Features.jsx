import React from "react";
import  "../styles.css";

const Features = () => {
  const benefits = [
    {
      img: "/assets/complexSystem.jpeg",
      title: "Complex Dynamic Systems",
      desc: "A playful experience of supply chain complexity.",
    },
    {
      img: "/assets/dataDriven.jpeg",
      title: "Data-driven Decision Making",
      desc: "Improve your data-driven decision-making skills and try to reach the game targets.",
    },
    {
      img: "/assets/modelsML.jpeg",
      title: "Models, Simulations & ML",
      desc: "Learn how to model complex systems, build interactive simulations, and use ML to make automated decisions.",
    },
    {
      img: "/assets/bullwhip.jpeg",
      title: "Bullwhip Effect",
      desc: "Experience the bullwhip effect known from real-life supply chains in a simulated environment.",
    },
  ];

  return (
    <section className="benefits-section" id="features">
      <h2>
        Discover the <span>Key Features</span>
      </h2>
      <div className="benefits-grid">
        {benefits.map((benefit, index) => (
          <div className="benefit-card" key={index}>
            <div className="benefit-image">
              <img src={benefit.img} alt={benefit.title} />
            </div>
            <h3>{benefit.title}</h3>
            <p>{benefit.desc}</p>
          </div>
        ))}
      </div>
    </section>
  );
};

export default Features;

