/*
    Just Journal JavaScript Library
    author: Lucas Holt
    date created: June 10, 2007
*/

function follow(friend) {
    'use strict';

        var request = jQuery.ajax({
            url: "/api/friend/" + friend,
            type: "PUT",
            data: {}
        });

        request.done(function () {
            window.alert('Now following ' + friend);
        });

        request.fail(function (jqXHR, textStatus) {
            window.alert("Could not follow friend. Request failed: " + textStatus);
        });
}

function unfollow(friend) {
    'use strict';

        var request = jQuery.ajax({
            url: "/api/friend/" + friend,
            type: "DELETE",
            data: {}
        });

        request.done(function () {
            window.alert('Unfollowing ' + friend);
        });

        request.fail(function (jqXHR, textStatus) {
            window.alert("Could not unfollow friend. Request failed: " + textStatus);
        });
}

function addFavorite(entryId) {
    'use strict';

    var request = jQuery.ajax({
        url: "/api/favorite/" + entryId,
        type: "POST",
        data: {}
    });

    request.done(function () {
        window.alert('Favorite saved.');
    });

    request.fail(function (jqXHR, textStatus) {
        window.alert("Favorite not saved. Request failed: " + textStatus);
    });
}

function deleteFavorite(entryId) {
    'use strict';

    var request = jQuery.ajax({
        url: "/api/favorite/" + entryId,
        type: "DELETE",
        data: {}
    });

    request.done(function () {
        window.alert('Favorite removed.');
    });

    request.fail(function (jqXHR, textStatus) {
        window.alert("Favorite not removed. Request failed: " + textStatus);
    });
}


function deleteEntry(entryId) {
    'use strict';
    if (confirmDelete()) {
        var request = jQuery.ajax({
            url: "/api/entry/" + entryId,
            type: "DELETE",
            data: {}
        });

        request.done(function() {
          window.alert('Removed Entry');
        });

        request.fail(function(jqXHR, textStatus) {
          window.alert("Request failed: " + textStatus);
        });
    }
}


function deleteComment(commentId) {
    'use strict';
    if (confirmDelete()) {
        var request = jQuery.ajax({
            url: "/api/comment/" + commentId,
            type: "DELETE",
            data: {}
        });

        request.done(function() {
          window.alert('Removed Comment');
        });

        request.fail(function(jqXHR, textStatus) {
          window.alert("Request failed: " + textStatus);
        });
    }
}

function confirmDelete() {
    'use strict';
    return window.confirm("Are you sure you want to delete this?");
}

function showbox(boxId) {
    'use strict';
    var box = document.getElementById(boxId);
    var parentBox = document.getElementById(boxId + "parent");
    box.style.top = getAbsY(parentBox) + "px";
    box.style.left = getAbsX(parentBox) + "px";
    box.style.visibility='visible';
}

function hidebox(boxId) {
    'use strict';
    var box = document.getElementById(boxId);
    box.style.visibility='hidden';
}

// get the true X offset of anything on NS4, IE4/5 &
// NS6, even if it's in a table!
function getAbsX(elt) {
    'use strict';
    return (elt.x) ? elt.x : getAbsPos(elt,"Left");
}

// get the true Y offset of anything on NS4, IE4/5 &
// NS6, even if it's in a table!
function getAbsY(elt) {
    'use strict';

    return (elt.y) ? elt.y : getAbsPos(elt,"Top");
}

function getAbsPos(elt,which) {
    'use strict';
    var iPos = 0;

    while (elt !== null) {
        iPos += elt["offset" + which];
        elt = elt.offsetParent;
    }
    
    return iPos;
}
