import  { createContext, useState,useContext } from 'react';
const AuthContext = createContext();
export  const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [role,setrole]=useState(null);
    const login=(username)=>{
        setUser(username);
        localStorage.setItem('user',JSON.stringify(username));
    };
    const logout=()=>{
        setUser(null);
        localStorage.removeItem('user');
    };
    return (
        <AuthContext.Provider value={{ user, role,setrole, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
}

export const useAuth = () => {
    return useContext(AuthContext);
}