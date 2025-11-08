export default function Data({ week, cost, demand }) {
  return (
    <div className="data-container">

      {/* Current Week */}
      <div className="data-box">
        <div><img src="" alt="week-icon" /></div>
        <div>
          <p>Current Week</p>
          <h2>{week}</h2>
        </div>
      </div>

      {/* Total Cost */}
      <div className="data-box">
        <div><img src="" alt="cost-icon" /></div>
        <div>
          <p>Total Cost</p>
          <h2>${cost}</h2>
        </div>
      </div>

      {/* Customer Demand */}
      <div className="data-box">
        <div><img src="" alt="demand-icon" /></div>
        <div>
          <p>Customer Demand</p>
          <h2>{demand} units</h2>
        </div>
      </div>

    </div>
  );
}
