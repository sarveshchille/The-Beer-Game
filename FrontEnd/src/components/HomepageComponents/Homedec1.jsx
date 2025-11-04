import React from 'react'; 
import benefit1 from '../../assets/images/benefit1.webp'
import benefit2 from '../../assets/images/benefit2.webp'
import benefit3 from '../../assets/images/benefit3.webp'
import benefit4 from '../../assets/images/benefit4.webp'
       

export default function Homedec() {
  return (
    <div className="bg-indigo-900 py-20">
      <h1 className="text-center text-4xl md:text-5xl font-extrabold text-white mb-12">
        Benefits: Much More Than a Supply Chain Simulation
      </h1>

      <div className="flex justify-center gap-10 px-6">


        <div className="text-center bg-white/10 backdrop-blur-md p-6 rounded-2xl shadow-lg hover:scale-105 transition transform">
          <img className="rounded-full w-24 h-24 mx-auto mb-4" src={benefit1} alt="" />
          <h3 className="text-xl font-bold text-indigo-300 mb-2">Complex Dynamic Systems</h3>
          <p className="text-gray-200 text-sm leading-relaxed">
            A playful experience of supply chain complexity.
          </p>
        </div>

        <div className="text-center bg-white/10 backdrop-blur-md p-6 rounded-2xl shadow-lg hover:scale-105 transition transform">
          <img className="rounded-full w-24 h-24 mx-auto mb-4" src={benefit2} alt="" />
          <h3 className="text-xl font-bold text-indigo-300 mb-2">Data-Driven Decision Making</h3>
          <p className="text-gray-200 text-sm leading-relaxed">
            Improve your data-driven decision making and reach game targets.
          </p>
        </div>

        <div className="text-center bg-white/10 backdrop-blur-md p-6 rounded-2xl shadow-lg hover:scale-105 transition transform">
          <img className="rounded-full w-24 h-24 mx-auto mb-4" src={benefit3} alt="" />
          <h3 className="text-xl font-bold text-indigo-300 mb-2">Models, Simulations & ML</h3>
          <p className="text-gray-200 text-sm leading-relaxed">
            Learn to model complex systems, simulate them, and use machine learning.
          </p>
        </div>

        <div className="text-center bg-white/10 backdrop-blur-md p-6 rounded-2xl shadow-lg hover:scale-105 transition transform">
          <img className="rounded-full w-24 h-24 mx-auto mb-4" src={benefit4} alt="" />
          <h3 className="text-xl font-bold text-indigo-300 mb-2">Bullwhip Effect</h3>
          <p className="text-gray-200 text-sm leading-relaxed">
            Experience the bullwhip effect seen in real-life supply chains.
          </p>
        </div>

      </div>
    </div>
  );
}
