import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

export default function LoginPage() {
  // Call hooks like useState and useNavigate at the top
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate(); // Hook to help us redirect

  const handleLogin = (e) => {
    e.preventDefault(); 
    
    // --- THIS IS WHERE WE WOULD CALL OUR BACKEND ---
   
    console.log('Logging in with:', { username, password });
    
    // Simulate a successful login by redirecting to the game
    navigate('/game'); 
  };

  return (
   
    <div className="flex items-center justify-center h-screen font-sans bg-gray-100">
      
      
      <form 
        onSubmit={handleLogin} 
        className="p-8 bg-white rounded-lg shadow-lg w-full max-w-xs sm:max-w-sm"
      >
        <h2 className="text-2xl font-bold text-center mb-6">Login</h2>
        
      
        <div className="mb-4">
          <label 
            htmlFor="username" 
            className="block mb-1 text-sm font-medium text-gray-700"
          >
            Username
          </label>
          <input
            type="text"
            id="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
           
            className="w-full p-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        
    
        <div className="mb-6">
          <label 
            htmlFor="password" 
            className="block mb-1 text-sm font-medium text-gray-700"
          >
            Password
          </label>
          <input
            type="password"
            id="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
           
            className="w-full p-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        
     
        <button 
          type="submit" 
          className="w-full p-3 text-white bg-blue-500 rounded-md cursor-pointer hover:bg-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 transition-colors"
        >
          Login
        </button>
        
       
        <p className="mt-4 text-sm text-center text-gray-600">
          Don't have an account?{' '}
          <Link 
            to="/signup" 
            className="font-medium text-blue-600 hover:text-blue-500"
          >
            Sign Up
          </Link>
        </p>
        <p className="mt-4 text-sm text-center text-gray-600">
          <Link 
            to="/" 
            className="font-medium text-blue-600 hover:text-blue-500"
          >
            Back to Home
          </Link>
        </p>
      </form>
    </div>
  );
}