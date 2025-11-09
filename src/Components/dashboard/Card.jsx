import { useState } from "react";
import { sendOrder } from "../../services/game-service";

export default function Card() {
  const userRole = localStorage.getItem("role");
 

  const [orderQuantities, setOrderQuantities] = useState({
    Retailer: 4,
    Wholesaler: 4,
    Distributor: 4,
    Factory: 4
  });

 
  

  const increaseOrder = (role) => {
    setOrderQuantities((prev) => {
      const newQty = prev[role] + 1;
      sendOrder(role, newQty);
      return { ...prev, [role]: newQty };
    });
  };


  const decreaseOrder = (role) => {
    setOrderQuantities((prev) => {
      const newQty = Math.max(0, prev[role] - 1);
      sendOrder(role, newQty);
      return { ...prev, [role]: newQty };
    });
  };

  return (
    <div className="card-container">
      {[userRole].map((role) => (
        <div key={role} className="card-item">

          <div className="card-top">
            <img src="" alt="" className="card-icon" />
            <h3>{role}</h3>
          </div>

          {/* Inventory & Backlog — will connect to backend later */}
          <div className="card-stats">
            <div>
              <p>Inventory</p>
              <h2>12 units</h2>
            </div>
            <div>
              <p className="backorder-label">Backorder</p>
              <h2 className="backorder-value">0 units</h2>
            </div>
          </div>

          {/* Order Controls */}
          <div className="card-order">
            <button onClick={() => decreaseOrder(role)}>-</button>
            <span>{orderQuantities[role]}</span>
            <button onClick={() => increaseOrder(role)}>+</button>
          </div>

          <p className="card-cost">Cost: $0.00</p>
        </div>
      ))}
    </div>
  );
}
