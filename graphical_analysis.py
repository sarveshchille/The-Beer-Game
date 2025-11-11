import matplotlib.pyplot as plt
import pandas as pd
import numpy as np
import textwrap

# --- Configuration ---
PLAYER_ROLES = ['Retailer', 'Wholesaler', 'Distributor', 'Producer']
FESTIVE_WEEKS = [7, 18] # Define our "chaos events"

# --- Dark Mode Style Configuration ---
STYLE = {
    'bg_color': "#05011C",
    'text_color': "#E9ECAF",
    'grid_color': '#444444',
    'spine_color': '#555555',
    'colors': {
        'Consumer': '#F0C000',     # Yellow
        'Retailer': '#00B050',     # Green
        'Wholesaler': '#00B0F0',   # Light Blue
        'Distributor': '#FF0000',  # Red
        'Producer': '#E0E0E0'      # White
    }
}

def generate_mock_data():
    """
    Generates realistic, 25-week mock data for all 4 players
    to demonstrate the "bullwhip effect."
    
    This includes "Festive Weeks" to show a demand shock.
    """
    print(f"Generating realistic mock data for 25 weeks...")
    print(f"Including Festive Week demand shocks at: {FESTIVE_WEEKS}")
    
    weeks = np.arange(1, 26)
    data = {}
    
    # --- 1. Create Consumer Demand (for Retailer) ---
    # Start with a base demand, then add spikes
    base_demand = pd.Series(20, index=weeks, name="customerOrders")
    base_demand.loc[5:12] = 40 # A planned spike
    
    # Add "Festive Week" shocks
    for week in FESTIVE_WEEKS:
        # Demand is double the *previous* week's demand
        base_demand.loc[week] = base_demand.loc[week - 1] * 2
    
    # This will hold the orders from the player one step downstream
    last_player_orders_placed = None

    for i, role in enumerate(PLAYER_ROLES):
        
        # --- 2. Get "Customer Orders" ---
        if role == 'Retailer':
            orders_received = base_demand
        else:
            # Get orders from the player below (with 1-week delay)
            orders_received = last_player_orders_placed.shift(1).fillna(20)
            orders_received.name = "customerOrders"

        # --- 3. Simulate "Orders Placed" (The Bullwhip) ---
        # Each player panics and orders *more* than they received.
        panic_multiplier = 1.0 + (i * 0.25) # Producer (i=3) panics the most
        
        orders_placed = (orders_received * panic_multiplier + np.random.randint(-5, 5, 25)).astype(int)
        orders_placed = orders_placed.clip(lower=0) 
        orders_placed.name = "newOrderPlaced"
        
        last_player_orders_placed = orders_placed 

        # --- 4. Simulate Deliveries, Inventory, and Backorders ---
        incoming_delivery = orders_placed.shift(3).fillna(20) # 3-week total delay
        incoming_delivery.name = "incomingDelivery"
        
        # Simulate inventory
        inventory = (100 - (orders_received * 0.5) + np.random.randint(-15, 15, 25) - i*15).astype(int)
        inventory = inventory.clip(lower=0)
        inventory.name = "inventory"
        
        # Simulate backorders
        backorder = (np.maximum(0, orders_received - (inventory * 0.8)) + np.random.randint(0, 10, 25)).astype(int)
        backorder = backorder.clip(lower=0)
        backorder.name = "backorder"

        # Simulate outgoing delivery
        outgoing_delivery = (orders_received - backorder).clip(lower=0)
        outgoing_delivery.name = "outgoingDelivery"
        
        # --- 5. Simulate Costs ---
        costs = (inventory * 0.5) + (backorder * 1.0)
        costs.name = "costs"
        
        cumulative_cost = costs.cumsum()
        cumulative_cost.name = "cumulativeCost"

        # --- 6. Combine into a DataFrame ---
        player_df = pd.concat([
            orders_received, orders_placed, inventory, backorder, 
            costs, cumulative_cost, incoming_delivery, outgoing_delivery
        ], axis=1)
        
        data[role] = player_df

    # Add Consumer demand separately for the comparison chart
    data['Consumer'] = pd.DataFrame({'newOrderPlaced': base_demand})
    
    print("Mock data ready!")
    return data, FESTIVE_WEEKS

def apply_dark_style(fig, ax, title, y_label, x_label="Game Week"):
    """A helper function to apply the cool dark mode style to any plot."""
    
    fig.patch.set_facecolor(STYLE['bg_color'])
    ax.set_facecolor(STYLE['bg_color'])

    # Title and labels
    ax.set_title(title, fontsize=18, fontweight='bold', loc='left', color=STYLE['text_color'], pad=20)
    ax.set_ylabel(y_label, fontsize=12, color=STYLE['text_color'])
    ax.set_xlabel(x_label, fontsize=12, color=STYLE['text_color'])

    # Spines (borders)
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['left'].set_color(STYLE['spine_color'])
    ax.spines['bottom'].set_color(STYLE['spine_color'])

    # Ticks (numbers on axes)
    ax.tick_params(axis='x', colors=STYLE['text_color'])
    ax.tick_params(axis='y', colors=STYLE['text_color'])
    
    # Grid
    ax.grid(axis='y', color=STYLE['grid_color'], linestyle='--', linewidth=0.5, alpha=0.5)
    
    # Ticks
    ax.set_xticks(np.arange(1, 26, 2)) # Show ticks every 2 weeks
    ax.set_xlim(0.5, 25.5)

def mark_events(ax, festive_weeks):
    """Draws a shaded vertical bar to highlight an event."""
    for week in festive_weeks:
        ax.axvspan(week - 0.5, week + 0.5, color='red', alpha=0.15, zorder=0, label='_nolegend_')
    
    # Add a dummy plot for the legend
    if festive_weeks:
        ax.plot([], [], color='red', alpha=0.15, linewidth=10, label='Festive Week (Demand Shock)')


def plot_individual_charts(data, role, festive_weeks):
    """
    Generates the 5 plots for a single player.
    """
    if role not in data:
        print(f"Error: Role '{role}' not found.")
        return
        
    player_df = data[role]
    weeks = player_df.index
    
    print(f"Displaying 5 individual graphs for: {role}")
    
    fig, axes = plt.subplots(5, 1, figsize=(12, 18), sharex=True)
    fig.set_facecolor(STYLE['bg_color'])
    
    # --- 1. Orders vs Week ---
    ax = axes[0]
    ax.plot(weeks, player_df['customerOrders'], label='Orders Received', marker='o', linestyle='--', color=STYLE['colors']['Wholesaler'])
    ax.plot(weeks, player_df['newOrderPlaced'], label='Orders Placed', marker='x', linestyle='-', color=STYLE['colors']['Retailer'])
    apply_dark_style(fig, ax, "Orders vs. Week", "Units")
    mark_events(ax, festive_weeks)
    ax.legend(facecolor=STYLE['bg_color'], edgecolor=STYLE['spine_color'], shadow=True)
    
    # --- 2. Inventory vs Week ---
    ax = axes[1]
    ax.plot(weeks, player_df['inventory'], label='Inventory', color=STYLE['colors']['Retailer'], marker='.')
    apply_dark_style(fig, ax, "Inventory vs. Week", "Units")
    mark_events(ax, festive_weeks)
    
    # --- 3. Backorder vs Week ---
    ax = axes[2]
    ax.plot(weeks, player_df['backorder'], label='Backorder', color=STYLE['colors']['Distributor'], marker='s', linestyle=':')
    apply_dark_style(fig, ax, "Backorder vs. Week", "Units")
    mark_events(ax, festive_weeks)

    # --- 4. Cost vs Week ---
    ax = axes[3]
    ax.plot(weeks, player_df['costs'], label='Weekly Cost', color='#F0C000', marker='^')
    apply_dark_style(fig, ax, "Weekly Cost vs. Week", "Cost ($)")
    mark_events(ax, festive_weeks)
    
    # --- 5. Cumulative Cost vs Week ---
    ax = axes[4]
    ax.plot(weeks, player_df['cumulativeCost'], label='Cumulative Cost', color=STYLE['colors']['Producer'], marker='d')
    apply_dark_style(fig, ax, "Cumulative Cost vs. Week", "Total Cost ($)")
    mark_events(ax, festive_weeks)
    
    plt.tight_layout(rect=[0, 0.03, 1, 0.96]) # Adjust for suptitle
    fig.suptitle(f"Individual Analysis for: {role}", fontsize=20, weight='bold', color=STYLE['text_color'])
    plt.show()

def plot_comparison_chart(data, column_name, title, y_label, festive_weeks):
    """
    A helper function to generate one comparison graph
    for all 4 players (and Consumer if it's the order plot).
    """
    print(f"Displaying 4-Player Comparison: {title}")
    
    fig, ax = plt.subplots(figsize=(15, 8), facecolor=STYLE['bg_color'])
    
    # Determine which players to plot
    players_to_plot = PLAYER_ROLES
    if column_name == 'newOrderPlaced':
        players_to_plot = ['Consumer'] + PLAYER_ROLES # Add consumer to this plot

    for i, role in enumerate(players_to_plot):
        series = data[role][column_name]
        color = STYLE['colors'][role]
        
        # Plot the line
        ax.plot(series.index, series, label=role, color=color, linewidth=2.5, zorder=i+1)
        
        # --- SPECIAL STYLING for the Bullwhip plot ---
        if column_name == 'newOrderPlaced':
            # Plot the fill area
            ax.fill_between(series.index, series, color=color, alpha=0.15, zorder=i)
            
            # Don't plot a peak marker for the Consumer
            if role == 'Consumer':
                continue

            # Find and plot the peak marker
            peak_week = series.idxmax()
            peak_value = series.max()
            ax.plot(peak_week, peak_value, 'o', color=color, markersize=12, 
                    markeredgecolor='white', markeredgewidth=1.5, zorder=i+2)
        
    # Apply standard dark style
    apply_dark_style(fig, ax, title, y_label)
    
    # Add event markers
    mark_events(ax, festive_weeks)
    
    # Style the legend
    leg = ax.legend(loc='lower center', ncol=5, bbox_to_anchor=(0.5, -0.25), 
                    frameon=False, fontsize=12)
    for text in leg.get_texts():
        text.set_color(STYLE['text_color'])
    
    plt.tight_layout()
    plt.show()

def main_menu():
    """
    Displays the main menu and handles user input.
    """
    # Generate the mock data once
    try:
        data, festive_weeks = generate_mock_data()
    except Exception as e:
        print(f"Error generating data: {e}")
        print("Please ensure pandas, matplotlib, and numpy are installed.")
        return

    while True:
        print("\n" + "="*40)
        print("  Beer Game Analysis Dashboard (Mock Data)")
        print("="*40)
        print("\nIndividual Player Reports:")
        print("  1. View Retailer Report (5 graphs)")
        print("  2. View Wholesaler Report (5 graphs)")
        print("  3. View Distributor Report (5 graphs)")
        print("  4. View Producer Report (5 graphs)")
        print("\n4-Player Comparison Graphs:")
        print(textwrap.fill("  5. Compare: Orders Placed (The Bullwhip Effect!)", 40))
        print("  6. Compare: Inventory")
        print("  7. Compare: Backorders")
        print("  8. Compare: Weekly Costs")
        print("  9. Compare: Cumulative Costs")
        print("\n  0. Exit")
        print("="*40)

        choice = input("Enter your choice (0-9): ")

        if choice == '1':
            plot_individual_charts(data, 'Retailer', festive_weeks)
        elif choice == '2':
            plot_individual_charts(data, 'Wholesaler', festive_weeks)
        elif choice == '3':
            plot_individual_charts(data, 'Distributor', festive_weeks)
        elif choice == '4':
            plot_individual_charts(data, 'Producer', festive_weeks)
        elif choice == '5':
            plot_comparison_chart(data, 'newOrderPlaced', '4-Player Comparison: Orders Placed (The Bullwhip Effect)', 'Units Ordered', festive_weeks)
        elif choice == '6':
            plot_comparison_chart(data, 'inventory', '4-Player Comparison: Inventory', 'Units in Stock', festive_weeks)
        elif choice == '7':
            plot_comparison_chart(data, 'backorder', '4-Player Comparison: Backorders', 'Units Owed', festive_weeks)
        elif choice == '8':
            plot_comparison_chart(data, 'costs', '4-Player Comparison: Weekly Costs', 'Weekly Cost ($)', festive_weeks)
        elif choice == '9':
            plot_comparison_chart(data, 'cumulativeCost', '4-Player Comparison: Total Cumulative Cost', 'Total Cost ($)', festive_weeks)
        elif choice == '0':
            print("Exiting analysis program.")
            break
        else:
            print(f"Invalid choice '{choice}'. Please enter a number from 0 to 9.")

if __name__ == "__main__":
    main_menu()