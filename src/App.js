import React from "react";
import Navbar from "./Components/Navbar";
import Hero from "./Components/Hero";
import Features from "./Components/Features";
import Gallery from "./Components/Gallery";
import Testimonials from "./Components/Testimonials";
import Footer from "./Components/Footer";
import "./styles.css";

/**
 * The main application component that renders the
 * Beer Game landing page layout using reusable sections.
 */
function App() {
  return (
    <div className="App">
      {/* Navigation Bar */}
      <Navbar />

      {/* Hero Section (main banner with title or image) */}
      <Hero />

      {/* Key features or highlights */}
      <Features />

      {/* Image gallery or demo visuals */}
      <Gallery />

      {/* User or team testimonials */}
      <Testimonials />

      {/* Footer Section */}
      <Footer />
    </div>
  );
}

export default App;
