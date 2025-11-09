export default function Header() {
  const username = localStorage.getItem("username");
  const role = localStorage.getItem("role");
  const roomId = localStorage.getItem("roomId");

  return (
    <div className="dashboard-header">
      <h1 className="dashboard-h1">Beer Distribution Game</h1>
      <p className="dashboard-p">
        Experience the bullwhip effect in action! Manage your role in the supply chain and <br />
        minimize costs.
      </p>

      <div className="player-info-bar">
        <span>Player: <b>{username}</b></span>
        <span> Role: <b>{role}</b></span>
        <span> Room ID: <b>{roomId}</b></span>
      </div>
    </div>
  );
}
