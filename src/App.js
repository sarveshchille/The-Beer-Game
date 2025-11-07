import { createBrowserRouter, RouterProvider } from "react-router-dom";
import LoginPage from "./pages/loginPage";
import HomePage from "./pages/Home";
import DashBoard from "./pages/DashBoard";
// ✅ Router config OUTSIDE function
const router = createBrowserRouter([
  { path: "/", element: <HomePage /> },
  { path: "/login", element: <LoginPage /> },
  {path:"/dashboard", element:<DashBoard /> }
]);

// ✅ App component
export default function App() {
  return <RouterProvider router={router} />;
}
