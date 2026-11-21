
const source = new EventSource("/events");

source.addEventListener("update-competition", event => {
    const competitionData = JSON.parse(event.data);
    const entries = competitionData.entries.sort((a, b) => a.stats.score - b.stats.score);
    
    console.log("Entry List",entries);
});

source.onmessage = event => {
    console.log(JSON.parse(event.data));
};

source.onerror = () => {
    console.log("Connection lost; browser will retry automatically");
};

console.log("connected", source);


