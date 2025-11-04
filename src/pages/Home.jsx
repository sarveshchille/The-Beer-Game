import React from "react";
import Hero from "../components/Hero";
import Features from "../components/Features";
import Gallery from "../components/Gallery";
import Testimonials from "../components/Testimonials";

export default function Home() {
  return (
    <div className="page home-page">
      <Hero />
      <Features />
      <Gallery />
      <Testimonials />
    </div>
  );
}
