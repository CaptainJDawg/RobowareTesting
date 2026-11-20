
class Player {
	constructor(inPlayer, inBot) {
		this.player = inPlayer;
		this.bot = inBot;
		this.points = 0;
		this.placement = 0;
		}

	getPoints() {
		return this.points;
	}

	addPoints(inPoints) {
		this.points += inPoints;
	}

	
	setPlacement(inPlace) {
		this.placement = inPlace;
	}

	getPlacement() {
		return this.placement;
	}
}

const players = [
	new Player("Eóin", "AAA"),
	new Player("Omar", "BBB"),
	new Player("Anastatsia", "CCC"),
	new Player("LuLu", "DDD"),
	new Player("Luke", "EEE"),
	new Player("Flourish", "FFF"),
	new Player("Oisin", "GGG")
];

const playerRows = document.querySelector("#player-ids");
const rowByPlayer = new Map();
const refreshRate = 2000;

function sort(players) {
	let sorted = false;
	while (!sorted) {
		sorted = true;
		for (let i = 1; i < players.length; i++) {
			const prev = players[i - 1];
			const cur = players[i];
			if (cur.getPoints() > prev.getPoints()) {
				players[i] = prev;
				players[i - 1] = cur;
				sorted = false;
			}
		}
	}
	for (let i = 0; i < players.length; i++) {
		players[i].setPlacement(i + 1);
	}
	return players;
} 

function createCell(text, className) {
	const cell = document.createElement("td");
	if (className) {
		cell.className = className;
	}
	cell.textContent = text;
	return cell;
}

function renderPlayers() {
	const rankedPlayers = sort(players);

	const previousPositions = new Map(
		Array.from(playerRows.children, row => [row, row.getBoundingClientRect().top])
	);

	// Build one table row for each ranked player.
	const rows = rankedPlayers.map(player => {
		let row = rowByPlayer.get(player);
		if (!row) {
			row = document.createElement("tr");
			rowByPlayer.set(player, row);
		}
		row.replaceChildren();

		// Use the placement set by the sort function.
		row.append(createCell(String(player.placement)));

		// Create the player name cell using the profile styles.
		const playerCell = document.createElement("td");
		const profile = document.createElement("div");
		profile.className = "profile";
		const name = document.createElement("div");
		name.className = "title";
		name.textContent = player.player;
		profile.append(name);
		playerCell.append(profile);
		row.append(playerCell, createCell(player.bot));

		const pointsCell = document.createElement("td");
		pointsCell.className = "points-cell";
		const points = document.createElement("span");
		points.textContent = player.getPoints();
		pointsCell.append(points);
		row.append(pointsCell);
		return row;
	});
	playerRows.replaceChildren(...rows);

	if (!window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
		for (const row of rows) {
			const previousTop = previousPositions.get(row);
			if (previousTop === undefined) {
				continue;
			}

			const verticalOffset = previousTop - row.getBoundingClientRect().top;
			if (verticalOffset !== 0) {
				row.animate(
					[
						{ transform: `translateY(${verticalOffset}px)` },
						{ transform: "translateY(0)" }
					],
					{ duration: refreshRate / 2, easing: "ease" }
				);
			}
		}
	}
}

function updatePlayers() {
// This is for testing.
	const player = players[Math.floor(Math.random() * players.length) + 1];
	player.addPoints(Math.floor(Math.random()*1000));

	// Sort the players again and update the table placements.
	renderPlayers();
}

// Give the page an initial render, then update it every two seconds.
renderPlayers();
setInterval(updatePlayers, refreshRate);