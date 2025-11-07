export default function FlowBox() {
  return (
    <div className="flow-container">
      <h2 className="flow-title">Supply Chain Flow</h2>

      <div className="flow-wrapper">

        {["Retailer","Wholesaler","Distributor","Factory"].map((role, i) => (
          <div key={role} className="flow-item">
            
            <div className="flow-node">
              <img src="" alt="" />
              <p>{role}</p>
            </div>

           
            {i !== 3 && (
              <div className="flow-arrow">
                <span>➜</span>
              </div>
            )}
          </div>
        ))}

      </div>

      <p className="flow-subtext">Orders move left → right • Beer moves right → left</p>
    </div>
  );
}
