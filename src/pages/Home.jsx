import React from "react";
import Hero from "../Components/Hero";
import Features from "../Components/Features";
import Gallery from "../Components/Gallery";
import Testimonials from "../Components/Testimonials";
import Footer from "../Components/Footer";
import Navbar from "../Components/Navbar";
export default function Home() {
  return (
    <div className="page home-page">
      <Navbar />
      <Hero />
      <Features />
      <Gallery />
      <Testimonials />
      {/* <JoinLobby /> */}
      <Footer  />
      
    </div>
  );
}
