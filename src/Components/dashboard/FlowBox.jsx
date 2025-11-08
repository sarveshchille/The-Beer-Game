export default function FlowBox() {
  const userRole = localStorage.getItem("role"); // current player's role

  return (
    <div className="flow-container">
      <h2 className="flow-title">Supply Chain Flow</h2>

      <div className="flow-wrapper">
        {["Retailer", "Wholesaler", "Distributor", "Factory"].map((role, i) => (
          <div key={role} className="flow-item">

            {/* Node */}
            <div className={`flow-node ${role === userRole ? "active-role" : ""}`}>
              <img src="" alt="" />
              <p>{role}</p>
            </div>

            {/* Arrow */}
            {i !== 3 && (
              <div className="flow-arrow">
                <span>➜</span>
              </div>
            )}
          </div>
        ))}
      </div>

      <p className="flow-subtext">
        Orders move left → right • Beer moves right → left
      </p>
    </div>
  );
}
