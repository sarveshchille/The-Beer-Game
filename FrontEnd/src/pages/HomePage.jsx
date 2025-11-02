import React from "react";
import { Link } from "react-router-dom";
import beerimg from "../assets/images/beerimg.webp";
import Homedec1 from "../components/HomepageComponents/Homedec1.jsx";

export default function HomePage() {
  return (
    <div className="bg-gradient-to-br from-indigo-900 via-purple-900 to-blue-900">

      {/* Hero Section */}
      <div className="min-h-screen flex items-center justify-center p-6">
        <div className="flex flex-col md:flex-row items-center max-w-6xl w-full mx-auto bg-white/95 backdrop-blur-sm rounded-2xl shadow-2xl overflow-hidden transform hover:scale-[1.01] transition-all duration-300 p-6 md:p-10">

          {/* Left */}
          <div className="md:w-1/2 p-4 md:p-6">
            <h1 className="text-4xl lg:text-5xl font-extrabold text-gray-900 leading-tight drop-shadow">
              Play the Beer Game<br />with Audiences of Any Size
            </h1>

            <p className="mt-4 text-lg text-gray-600">
              A playful experience of supply chain complexity.
            </p>

            <div className="mt-8 flex flex-col sm:flex-row gap-4">
              <Link
                to="/signup"
                className="px-8 py-3 text-lg font-semibold text-white bg-blue-600 rounded-lg shadow-lg hover:bg-blue-700 hover:shadow-blue-500/50 transform hover:translate-x-1 transition duration-300"
              >
                Sign Up
              </Link>

              <Link
                to="/login"
                className="px-8 py-3 text-lg font-semibold text-blue-700 bg-gray-100 rounded-lg shadow-lg hover:bg-gray-200 transform hover:translate-x-1 transition duration-300"
              >
                Login
              </Link>
            </div>
          </div>

          {/* Right */}
          <div className="md:w-1/2 w-full p-4">
            <img
              src={beerimg}
              alt="Beer Game Illustration"
              className="rounded-xl shadow-lg w-full h-auto object-cover hover:scale-105 transition duration-300"
            />
          </div>

        </div>
      </div>

      {/* ✅ Homedec below scroll */}
      <Homedec1 />
    </div>
  );
}
