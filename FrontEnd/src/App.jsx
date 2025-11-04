import React from 'react';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';


import LoginPage from './pages/LoginPage.jsx';
import SignupPage from './pages/SignupPage.jsx';
import HomePage from './pages/HomePage.jsx'; 
import GamePage from './pages/GamePage.jsx';
const router = createBrowserRouter([
  {
    path: "/", 
    element: < HomePage />,
  },
  {
    path: "/login",
    element: <LoginPage />,
  },
  {
    path: "/signup",
    element: <SignupPage />,
  },
  {
    path: "/game",
    element: <GamePage />,
  }
  
]);

function App() {

  return (
    <RouterProvider router={router} />
  );
}

export default App;

