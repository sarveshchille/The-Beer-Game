import { createBrowserRouter, RouterProvider } from "react-router-dom";
import LoginPage from "./pages/loginPage";
import HomePage from "./pages/Home";
import DashBoard from "./pages/DashBoard";
import Check from "./Check";
import SignUpPage from "./pages/SignUpPage";

const router = createBrowserRouter([
  { path: "/", element: <HomePage /> },
  { path: "/login", element: <LoginPage /> },
  {path:"/dashboard", element:<DashBoard /> },
  {path:"/check", element:<Check /> },
  {path:"/sign", element:<SignUpPage /> }
]);


export default function App() {
  return <RouterProvider router={router} />;
}
