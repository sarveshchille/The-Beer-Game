export default function HowToPlay() {
  return (
    <div className="how-container">
        <button className="start-btn">
        ▶ Start Game
      </button>
      <h2 className="how-title">How to Play</h2>

      <ul className="how-list">
        <li><span>🎯</span> Goal: Minimize your total supply chain cost</li>
        <li><span>📦</span> Inventory costs $0.50 per unit per week</li>
        <li><span>⚠️</span> Backlog costs $1.00 per unit per week</li>
        <li><span>🚚</span> Orders & shipments take 2 weeks to arrive</li>
        <li><span>🌊</span> Small demand changes cause big upstream swings</li>
      </ul>

      
    </div>
  );
}
