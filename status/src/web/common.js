/**
 * Inits the grid of the status page, performs the JSON fetch.
 */
function initGrid() {
    fetch('https://api.status.comroid.org/services_bulk_status')
		.then(handleErrors)
		.then(response => response.json())
        .then(data => createBoxes(data))
        .catch(error => {
			addBox("status-server", "Status Server", "unknown");
			console.error('There has been a problem with your fetch operation:', error);
		});
}

/**
 * Function to handle failed HTTP responses.
 * 
 * @param {Object} response The response object of the fetch API
 */
function handleErrors(response) {
    if (!response.ok) {
        throw Error(response.statusText);
    }
    return response;
}

/**
 * Creates all boxes for the status page grid.
 * 
 * @param {Object} data The JSON data delivered by the Fetch API request
 */
function createBoxes(data) {
    for (var i = 0; i < data.results.length; i++) {
        var obj = data.results[i];
        addBox(obj.name, obj.display_name, determineStatus(obj.status));
    }
}

/**
 * Translates the Status Number to a String.
 * A string is required to set the correct CSS classes and texts inside a service box.
 * 
 * @param {Number} number The status number delivered by the Fetch API request
 */
function determineStatus(number) {
    switch (number) {
        case 1:
            return "offline";
            break;
        case 2:
            return "maintenance";
            break;
        case 3:
            return "busy";
            break;
        case 4:
            return "online";
            break;
    }
}

/**
 * Creates a new div inside the flex container.
 *
 * @param {string} name The service name parsed from JSON data
 * @param {string} display_name The display name parsed from JSON data
 * @param {string} status The current status of the service
 */
function addBox(name, display_name, status) {
    // Create a new box in the status grid
    var newDiv = document.createElement("div");
    if (name == "status-server") {
        newDiv.id = "head_wrapper";
    } else {
        newDiv.className = "cell";
    }

    // Add text and status div depending on previous JSON parsing
    var statusDiv = document.createElement("div");
    var statusText = document.createElement("p");
    statusText.appendChild(document.createTextNode(display_name + ":"));
    statusDiv.className = status;
    statusDiv.innerHTML = status.toUpperCase();

    // Append text and status div to the box
    newDiv.appendChild(statusText);
    newDiv.appendChild(statusDiv);

    // Add the box to the flex container
    document.getElementById('flex_container').appendChild(newDiv);
}
