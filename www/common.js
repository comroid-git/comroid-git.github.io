const hash = window.location.hash.substr(1);

function initContent() {
    console.debug("Hash: "+hash);

    const contentBox = document.getElementById('content');
    if (hash.length === 0) {
        const homepage = pages['homepage'];
        insertFrame(contentBox, homepage['path'], homepage['id'])
        return;
    }

    const print = resolveTarget(hash);
    if (print.length < 1) {
        console.error("No pages could be found")
        return;
    }

    const wrapper = generateContentWrapper(contentBox, print);
    print.forEach(function (page) {
        insertFrame(wrapper, page['path'], page['id']);
    });
}

// content insertion
function insertFrame(parent, url, pageId) {
    const frame = document.createElement('iframe');

    frame.className = 'content-container';
    frame.id = `content-container-${pageId}`
    frame.setAttribute('src', url);

    parent.appendChild(frame);
    return frame;
}

function generateContentWrapper(parent, pages) {
    if (pages.length === 1)
        return parent;

    const wrapper = document.createElement('div')
    wrapper.id = 'content-wrapper';

    // todo add select buttons

    parent.appendChild(wrapper);
    return wrapper;
}

// content resolving
function resolveTarget(mask) {
    console.debug(`Resolving Content for mask: ${mask}`)

    let yields = [];

    for (key in pages) {
        const page = pages[key];

        if (isSet(page.id, mask))
            yields.push(page);
    }

    const length = yields.length;
    if (length === 0)
        yields.push(pages['not_found']);

    console.debug(`Found ${length} page${length === 1 ? '' : 's'}`)

    return yields;
}

function resolveContent(page) {
    // based on https://stackoverflow.com/questions/10932226/how-do-i-get-source-code-from-a-webpage

    // todo find current url and always go to root dir
    let url = "./" + page['path'], xmlhttp; //Remember, same domain

    if ("XMLHttpRequest" in window)
        xmlhttp = new XMLHttpRequest();
    if ("ActiveXObject" in window)
        xmlhttp = new ActiveXObject("Msxml2.XMLHTTP");

    if (xmlhttp === undefined)
        return "Could not request content of " + page['display_name'];

    xmlhttp.open('GET', url, false);
    xmlhttp.send(null);

    if (xmlhttp.response.status)
        return missingPage(page);
    return xmlhttp.responseText;
}

function missingPage(page) {
    return "Unable to fetch content of " + page['display_name'];
}

function isSet(flag, inMask) {
    return (inMask & flag) !== 0;
}
