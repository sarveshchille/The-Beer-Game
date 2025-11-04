import {react,useState} from "react";
import { Link, useNavigate } from "react-router-dom";
export default function SignupPage() {
    const [username, setUsername] = useState('');   
    const [password, setPassword] = useState('');
    const [email, setEmail] = useState('');
    const navigate = useNavigate(); // Hook to help us redirect
    const handleSignup = (e) => {
        e.preventDefault();
        // --- THIS IS WHERE WE WOULD CALL OUR BACKEND ---
        console.log('Signing up with:', { username, password });    
        login(username);
navigate('/game');
 
    };
  return (
    <div>
        <div>
        <div className="flex items-center justify-center h-screen font-sans bg-gray-100">
            <form
            onSubmit={handleSignup} 
            className="p-8 bg-white rounded-lg shadow-lg w-full max-w-xs sm:max-w-sm"
            >
            <h2 className="text-2xl font-bold text-center mb-6">Sign Up</h2>
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
            <div className="mb-6">
                <label  
                htmlFor="email"
                className="block mb-1 text-sm font-medium text-gray-700"
                >
                Email
                </label>
                <input      
                type="email"
                id="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required    
                className="w-full p-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
            </div>
            <button
                type="submit"
                className="w-full bg-blue-600 text-white py-2 px-4 rounded-md hover:bg-blue-700 transition duration-300"
            >
                Sign Up
            </button>

            <p className="mt-4 text-sm text-center text-gray-600">
                Already have an account?{' '}
                <Link 
                to="/login" 
                className="font-medium text-blue-600 hover:text-blue-500"
                >
                Login
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
        </div>
    </div>
  );
}