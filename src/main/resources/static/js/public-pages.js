(function ($) {
    'use strict';

    function escapeHtml(value) {
        return $('<div/>').text(value == null ? '' : String(value)).html();
    }

    function escapeAttr(value) {
        return escapeHtml(value).replace(/"/g, '&quot;');
    }

    function showMessage(selector, message, type) {
        var $el = $(selector);
        if (!message) {
            $el.hide().text('').removeClass('alert-danger alert-success');
            return;
        }

        $el
            .removeClass('alert-danger alert-success')
            .addClass(type === 'success' ? 'alert-success' : 'alert-danger')
            .html(message)
            .show();
    }

    function parseError(xhr, fallback) {
        if (xhr && xhr.responseJSON) {
            if (xhr.responseJSON.error) {
                return xhr.responseJSON.error;
            }
            if (xhr.responseJSON.Message) {
                return xhr.responseJSON.Message;
            }
        }

        if (xhr && xhr.responseText) {
            return xhr.responseText;
        }

        return fallback;
    }

    function currentUsername() {
        var username = $('body').attr('data-authenticated-username');
        if (!username || username === 'null') {
            return '';
        }
        return username;
    }

    function pageData(name) {
        var value = $('body').attr('data-' + name);
        return (!value || value === 'null') ? '' : value;
    }

    function track(eventName, category, label) {
        if (typeof window.gtag === 'function') {
            window.gtag('event', eventName, {
                event_category: category,
                event_label: label
            });
        }
    }

    function setProgress(selector, percent) {
        var pct = Math.max(0, Math.min(100, percent));
        $(selector).css('width', pct + '%').text(pct + '%');
    }

    function initHome() {
        $('#login-form').on('submit', function (event) {
            event.preventDefault();
            showMessage('#login-error', '');

            var username = $('#login-username').val().trim();
            var password = $('#login-password').val();

            if (username.length < 3) {
                showMessage('#login-error', 'Username must be greater than 2 characters');
                return;
            }

            if (password.length < 5) {
                showMessage('#login-error', 'Password must be greater than 4 characters');
                return;
            }

            $.ajax({
                url: '/api/login',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({username: username, password: password})
            }).done(function (response) {
                if (response && response.status === 'JJ.LOGIN.OK') {
                    track('login', 'Authentication', 'Login');
                    window.location.href = '/users/' + response.username;
                    return;
                }

                showMessage('#login-error', 'Your login information was invalid. Please try again');
            }).fail(function () {
                showMessage('#login-error', 'Your login information was invalid. Please try again');
            });
        });

        $('#signup-form').on('submit', function (event) {
            event.preventDefault();
            showMessage('#signup-error', '');

            var payload = {
                firstName: $('#signup-fname').val().trim(),
                lastName: $('#signup-lname').val().trim(),
                email: $('#signup-email').val().trim(),
                username: $('#signup-username').val().trim(),
                password: $('#signup-password').val()
            };

            if (payload.password !== $('#signup-password2').val()) {
                showMessage('#signup-error', 'Passwords do not match');
                return;
            }

            $.ajax({
                url: '/api/signup',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(payload)
            }).done(function () {
                track('sign_up', 'Account', 'Signup');

                $.ajax({
                    url: '/api/login',
                    method: 'POST',
                    contentType: 'application/json',
                    data: JSON.stringify({username: payload.username, password: payload.password})
                }).done(function (response) {
                    if (response && response.status === 'JJ.LOGIN.OK') {
                        window.location.href = '/users/' + response.username;
                        return;
                    }
                    showMessage('#signup-error', 'Your account was created, but automatic login failed.');
                }).fail(function () {
                    showMessage('#signup-error', 'Your account was created, but automatic login failed.');
                });
            }).fail(function (xhr) {
                showMessage('#signup-error', parseError(xhr, 'Unable to create this account. Please verify all fields and try again'));
            });
        });
    }

    function initMembers() {
        $.getJSON('/api/members').done(function (members) {
            var rows = [];
            $.each(members || [], function (_, member) {
                rows.push(
                    '<tr>' +
                    '<td><a href="/users/' + encodeURIComponent(member.username) + '">' + escapeHtml(member.username) + '</a></td>' +
                    '<td>' + escapeHtml(member.name) + '</td>' +
                    '<td>' + escapeHtml(member.since) + '</td>' +
                    '<td><a href="/profile/' + encodeURIComponent(member.username) + '">view profile</a></td>' +
                    '</tr>'
                );
            });
            $('#members-list').html(rows.join(''));
            $('#members-count').text((members || []).length);
        }).fail(function (xhr) {
            showMessage('#members-error', parseError(xhr, 'Unable to load members'));
        });
    }

    function initMoodList() {
        $.getJSON('/api/mood').done(function (moods) {
            var rows = [];
            $.each(moods || [], function (_, mood) {
                rows.push(
                    '<tr>' +
                    '<td>' + escapeHtml(mood.id) + '</td>' +
                    '<td>' + escapeHtml(mood.parent) + '</td>' +
                    '<td>' + escapeHtml(mood.title) + '</td>' +
                    '</tr>'
                );
            });
            $('#mood-list').html(rows.join(''));
        }).fail(function (xhr) {
            showMessage('#mood-error', parseError(xhr, 'Unable to load moods'));
        });
    }

    function renderSearchResults(results) {
        var entries = (results && results._embedded && results._embedded.blogEntries) ? results._embedded.blogEntries : [];
        var html = [];

        $.each(entries, function (_, entry) {
            html.push(
                '<div>' +
                '<h4 style="color: orange;">' + escapeHtml(entry.date) + '</h4>' +
                '<div>' +
                '<h5><img src="/images/userclass_16.png" alt="user"/> ' +
                '<a href="/users/' + encodeURIComponent(entry.author) + '" style="font-size: 12px;">' + escapeHtml(entry.author) + '</a>' +
                ' - <span class="time">' + escapeHtml(entry.date) + '</span> - ' +
                '<span class="subject"><a href="/users/' + encodeURIComponent(entry.author) + '/entry/' + encodeURIComponent(entry.id) + '">' +
                escapeHtml(entry.subject) +
                '</a></span></h5>' +
                '<div class="ebody">' + (entry.body || '') + '</div>' +
                '</div>' +
                '</div>'
            );
        });

        if (html.length === 0) {
            html.push('<p>No results found.</p>');
        }

        $('#search-results').html(html.join(''));
    }

    function initSearch() {
        $('#bsearch').on('submit', function (event) {
            event.preventDefault();
            showMessage('#search-error', '');

            $.getJSON('/api/search', {term: $('#bquery').val().trim()})
                .done(renderSearchResults)
                .fail(function (xhr) {
                    showMessage('#search-error', parseError(xhr, 'Search failed'));
                });
        });
    }

    function initStats() {
        $.getJSON('/api/statistics').done(function (stats) {
            $('#stats-users').text(stats.users || 0);
            $('#stats-entries').text(stats.entries || 0);
            $('#stats-comments').text(stats.comments || 0);
            $('#stats-styles').text(stats.styles || 0);
            $('#stats-tags').text(stats.tags || 0);
        }).fail(function (xhr) {
            showMessage('#stats-error', parseError(xhr, 'Unable to load statistics'));
        });
    }

    function initTags() {
        $.getJSON('/api/tags').done(function (tags) {
            var largest = 0;
            var smallest = 0;
            var html = [];

            $.each(tags || [], function (_, tag) {
                if (tag.count > largest) {
                    largest = tag.count;
                }
                if (tag.count < smallest) {
                    smallest = tag.count;
                }
            });

            var cutsmall = largest / 3;
            var cutlarge = cutsmall * 2;

            $.each(tags || [], function (_, tag) {
                var cssClass = 'TagCloudMedium';
                if (tag.count > cutlarge) {
                    cssClass = 'TagCloudLarge';
                } else if (tag.count < cutsmall) {
                    cssClass = 'TagCloudSmall';
                }
                html.push('<span class="' + cssClass + '">' + escapeHtml(tag.name) + ' </span>');
            });

            $('#tags-cloud').html(html.join(''));
        }).fail(function (xhr) {
            showMessage('#tags-error', parseError(xhr, 'Unable to load tags'));
        });
    }

    function initProfile() {
        var username = pageData('profile-username');
        if (!username) {
            return;
        }

        $('#profile-friends-link').attr('href', '/users/' + encodeURIComponent(username) + '/friends');

        $.when(
            $.getJSON('/api/account/' + encodeURIComponent(username)),
            $.getJSON('/api/journal/user/' + encodeURIComponent(username)),
            $.getJSON('/api/statistics/' + encodeURIComponent(username)),
            $.getJSON('/api/friend/' + encodeURIComponent(username)),
            $.getJSON('/api/biography/' + encodeURIComponent(username))
        ).done(function (accountRes, journalRes, statsRes, friendsRes, bioRes) {
            var account = accountRes[0] || {};
            var journals = journalRes[0] || [];
            var stats = statsRes[0] || {};
            var friends = friendsRes[0] || [];
            var bio = bioRes[0] || {};
            var journalNames = [];
            var friendsHtml = [];
            var userId = account.id || account.userId;
            var showAvatar = account.showAvatar === true || account.showAvatar === 'Y' || (account.userPref && account.userPref.showAvatar === 'Y');

            $.each(journals, function (_, journal) {
                journalNames.push(journal.name);
            });

            $.each(friends, function (_, friend) {
                friendsHtml.push('<img src="/images/userclass_16.png" alt="users"> <a href="/users/' + encodeURIComponent(friend.username) + '">' + escapeHtml(friend.username) + '</a><br>');
            });

            if (showAvatar && userId) {
                $('#profile-avatar-image')
                    .attr('src', '/Avatar/' + userId)
                    .attr('alt', username + '\'s avatar');
                $('#profile-avatar').show();
            }

            $('#profile-general').html(
                'user: <a href="/users/' + encodeURIComponent(username) + '">' + escapeHtml(username) + '</a>' +
                '<br>name: ' + escapeHtml(account.firstName || '') +
                '<br>my journals: ' + escapeHtml(journalNames.join(', ')) +
                '<br>Member since: ' + escapeHtml(account.since || '')
            );
            $('#profile-biography').text((bio && bio.bio) || '');
            $('#profile-friends').html(friendsHtml.join(''));
            $('#profile-entry-count').text(stats.entryCount || 0);
            $('#profile-comment-count').text(stats.commentCount || 0);
        }).fail(function (xhr) {
            showMessage('#profile-error', parseError(xhr, 'Unable to load profile'));
        });

        // contact information is only available to logged in users
        if (!currentUsername()) {
            return;
        }

        $.getJSON('/api/contact/' + encodeURIComponent(username)).done(function (contact) {
            var items = [];

            if (contact.email) {
                items.push('<p><i class="fa fa-envelope"></i> Email: <a href="mailto:' + escapeAttr(contact.email) + '">' + escapeHtml(contact.email) + '</a></p>');
            }
            if (contact.phone) {
                items.push('<p><i class="fa fa-phone"></i> Phone: ' + escapeHtml(contact.phone) + '</p>');
            }
            if (contact.x) {
                items.push('<p><i class="fa fa-twitter"></i> X (Twitter): <a target="_blank" href="https://twitter.com/' + encodeURIComponent(contact.x) + '">' + escapeHtml(contact.x) + '</a></p>');
            }
            if (contact.instagram) {
                items.push('<p><i class="fa fa-instagram"></i> Instagram: <a target="_blank" href="https://www.instagram.com/' + encodeURIComponent(contact.instagram) + '">' + escapeHtml(contact.instagram) + '</a></p>');
            }
            if (contact.facebook) {
                items.push('<p><i class="fa fa-facebook"></i> Facebook: <a target="_blank" href="https://www.facebook.com/' + encodeURIComponent(contact.facebook) + '">' + escapeHtml(contact.facebook) + '</a></p>');
            }
            if (contact.telegram) {
                items.push('<p><i class="fa fa-telegram"></i> Telegram: <a target="_blank" href="https://t.me/' + encodeURIComponent(contact.telegram) + '">' + escapeHtml(contact.telegram) + '</a></p>');
            }
            if (contact.linkedin) {
                items.push('<p><i class="fa fa-linkedin"></i> LinkedIn: <a target="_blank" href="https://www.linkedin.com/in/' + encodeURIComponent(contact.linkedin) + '">' + escapeHtml(contact.linkedin) + '</a></p>');
            }
            if (contact.reddit) {
                items.push('<p><i class="fa fa-reddit"></i> Reddit: <a target="_blank" href="https://www.reddit.com/user/' + encodeURIComponent(contact.reddit) + '">' + escapeHtml(contact.reddit) + '</a></p>');
            }
            if (contact.hpUri) {
                items.push('<p><i class="fa fa-globe"></i> Homepage: <a target="_blank" href="' + escapeAttr(contact.hpUri) + '">' + escapeHtml(contact.hpTitle || contact.hpUri) + '</a></p>');
            }

            if (items.length > 0) {
                $('#profile-contact').html(items.join(''));
                $('#profile-contact-section').show();
            }
        });
    }

    function initEditor(id) {
        if (window.CKEDITOR && document.getElementById(id) && !window.CKEDITOR.instances[id]) {
            window.CKEDITOR.replace(id);
        }
    }

    function getEditorValue(id) {
        if (window.CKEDITOR && window.CKEDITOR.instances[id]) {
            var html = window.CKEDITOR.instances[id].getData();
            return typeof window.toMarkdown === 'function' ? window.toMarkdown(html) : html;
        }
        return $('#' + id).val();
    }

    function setEditorValue(id, value) {
        if (window.CKEDITOR && window.CKEDITOR.instances[id]) {
            window.CKEDITOR.instances[id].setData(value || '');
            return;
        }
        $('#' + id).val(value || '');
    }

    function splitTags(raw) {
        var normalized = (raw || '').toLowerCase().replace(/\s+/g, ',');
        var values = normalized.split(',');
        var tags = [];

        $.each(values, function (_, value) {
            if (value) {
                tags.push(value);
            }
        });

        return tags;
    }

    function loadJsonList(url, onSuccess, onError) {
        $.getJSON(url).done(onSuccess).fail(function (xhr) {
            if (onError) {
                onError(xhr);
            }
        });
    }

    function populateSelect($select, items, valueKey, textKey, selectedValue) {
        var html = [];
        $.each(items || [], function (_, item) {
            var value = item[valueKey];
            html.push('<option value="' + escapeAttr(value) + '">' + escapeHtml(item[textKey]) + '</option>');
        });
        $select.html(html.join(''));
        if (selectedValue !== undefined && selectedValue !== null && selectedValue !== '') {
            $select.val(String(selectedValue));
        }
    }

    function initEntry() {
        initEditor('body');
        var username = currentUsername();
        var entryId = pageData('entry-id');
        var defaults = {
            allowComments: true,
            autoFormat: true,
            draft: false,
            emailComments: true,
            format: 'MARKDOWN',
            subject: '',
            body: '',
            tags: [],
            music: '',
            trackback: '',
            security: 0,
            mood: 12,
            location: 0
        };

        $.when(
            $.getJSON('/api/mood'),
            $.getJSON('/api/location'),
            $.getJSON('/api/security')
        ).done(function (moodRes, locationRes, securityRes) {
            var moods = moodRes[0] || [];
            var locations = locationRes[0] || [];
            var security = securityRes[0] || [];

            populateSelect($('#mood'), moods, 'id', 'title', defaults.mood);
            populateSelect($('#location'), locations, 'id', 'title', defaults.location);
            populateSelect($('#security'), security, 'id', 'name', defaults.security);

            if (!entryId || !username) {
                return;
            }

            $.getJSON('/api/entry/' + encodeURIComponent(username) + '/eid/' + encodeURIComponent(entryId))
                .done(function (entry) {
                    $('#entry-subject').val(entry.subject || '');
                    setEditorValue('body', entry.body || '');
                    $('#tags').val((entry.tags || []).join(' '));
                    $('#music').val(entry.music || '');
                    $('#trackback').val(entry.trackback || '');
                    $('#allow_comment').prop('checked', entry.allowComments !== false);
                    $('#email_comment').prop('checked', entry.emailComments !== false);
                    $('#mood').val(String((entry.mood && entry.mood.id) || entry.mood || defaults.mood));
                    $('#location').val(String((entry.location && entry.location.id) || entry.location || defaults.location));
                    $('#security').val(String((entry.security && entry.security.id) || entry.security || defaults.security));
                })
                .fail(function (xhr) {
                    showMessage('#entry-error', parseError(xhr, 'Unable to load entry'));
                });
        }).fail(function (xhr) {
            showMessage('#entry-error', parseError(xhr, 'Unable to load editor options'));
        });

        $('#entry-cancel').on('click', function () {
            window.location.href = username ? '/users/' + username : '/';
        });

        $('#frmUpdateJournal').on('submit', function (event) {
            event.preventDefault();
            showMessage('#entry-error', '');
            showMessage('#entry-success', '');

            var payload = {
                entryId: entryId ? parseInt(entryId, 10) : 0,
                subject: $('#entry-subject').val().trim(),
                body: getEditorValue('body'),
                music: $('#music').val().trim(),
                trackback: $('#trackback').val().trim(),
                format: 'MARKDOWN',
                autoFormat: true,
                allowComments: $('#allow_comment').is(':checked'),
                emailComments: $('#email_comment').is(':checked'),
                draft: false,
                security: parseInt($('#security').val(), 10),
                mood: parseInt($('#mood').val(), 10),
                location: parseInt($('#location').val(), 10),
                tags: splitTags($('#tags').val())
            };

            if (!payload.body) {
                showMessage('#entry-error', 'Entry does not contain a body.');
                return;
            }

            $.ajax({
                url: '/api/entry',
                method: entryId ? 'PUT' : 'POST',
                contentType: 'application/json',
                data: JSON.stringify(payload)
            }).done(function () {
                if (username) {
                    window.location.href = '/users/' + username;
                    return;
                }
                showMessage('#entry-success', 'Entry saved successfully.', 'success');
            }).fail(function (xhr) {
                showMessage('#entry-error', parseError(xhr, 'Unable to save entry'));
            });
        });
    }

    function initComment() {
        initEditor('commentbody');
        var username = currentUsername();
        var entryId = pageData('entry-id');
        var commentId = pageData('comment-id');

        if (commentId) {
            $.getJSON('/api/comment/' + encodeURIComponent(commentId)).done(function (comment) {
                $('#subject').val(comment.subject || '');
                setEditorValue('commentbody', comment.body || '');
            }).fail(function (xhr) {
                showMessage('#comment-error', parseError(xhr, 'Unable to load comment'));
            });
        }

        $('#comment-cancel').on('click', function () {
            window.location.href = username ? '/users/' + username : '/';
        });

        $('#frmAddComment').on('submit', function (event) {
            event.preventDefault();
            showMessage('#comment-error', '');
            showMessage('#comment-success', '');

            var payload = {
                id: commentId ? parseInt(commentId, 10) : 0,
                eid: entryId ? parseInt(entryId, 10) : 0,
                subject: $('#subject').val().trim(),
                body: getEditorValue('commentbody'),
                format: 'MARKDOWN'
            };

            if (!payload.body) {
                showMessage('#comment-error', 'Comment body is required.');
                return;
            }

            $.ajax({
                url: '/api/comment',
                method: commentId ? 'PUT' : 'POST',
                contentType: 'application/json',
                data: JSON.stringify(payload)
            }).done(function () {
                if (username) {
                    window.location.href = '/users/' + username;
                    return;
                }
                showMessage('#comment-success', 'Comment saved successfully.', 'success');
            }).fail(function (xhr) {
                showMessage('#comment-error', parseError(xhr, 'Unable to save comment'));
            });
        });
    }

    function uploadForm(formSelector, url, successSelector, errorSelector, progressContainerSelector, progressSelector, extraData, onDone) {
        $(formSelector).on('submit', function (event) {
            event.preventDefault();
            showMessage(errorSelector, '');
            showMessage(successSelector, '');

            var formData = new FormData(this);
            if (extraData) {
                $.each(extraData(), function (key, value) {
                    formData.append(key, value);
                });
            }

            $(progressContainerSelector).show();
            setProgress(progressSelector, 0);

            $.ajax({
                url: url,
                method: 'POST',
                data: formData,
                processData: false,
                contentType: false,
                xhr: function () {
                    var xhr = $.ajaxSettings.xhr();
                    if (xhr.upload) {
                        xhr.upload.addEventListener('progress', function (evt) {
                            if (evt.lengthComputable) {
                                setProgress(progressSelector, Math.round((evt.loaded / evt.total) * 100));
                            }
                        }, false);
                    }
                    return xhr;
                }
            }).done(function () {
                setProgress(progressSelector, 100);
                showMessage(successSelector, 'Upload successful.', 'success');
                if (onDone) {
                    onDone();
                }
            }).fail(function (xhr) {
                showMessage(errorSelector, parseError(xhr, 'Upload failed'));
            });
        });
    }

    function initBiography() {
        var username = currentUsername();
        if (!username) {
            showMessage('#biography-error', 'You must be logged in to edit your biography.');
            return;
        }

        $.getJSON('/api/biography/' + encodeURIComponent(username)).done(function (bio) {
            $('#bio').val((bio && bio.bio) || '');
        });

        $('#frmBiography').on('submit', function (event) {
            event.preventDefault();
            showMessage('#biography-error', '');
            showMessage('#biography-success', '');

            $.ajax({
                url: '/api/biography',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify($('#bio').val())
            }).done(function () {
                track('biography_upload', 'Preferences', 'BiographyUpdate');
                showMessage('#biography-success', 'Biography changed.', 'success');
            }).fail(function (xhr) {
                showMessage('#biography-error', parseError(xhr, 'Unable to save biography'));
            });
        });
    }

    function initPassword() {
        $('#frmPasswd').on('submit', function (event) {
            event.preventDefault();
            showMessage('#password-error', '');
            showMessage('#password-success', '');

            $.ajax({
                url: '/api/account/password',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({
                    passCurrent: $('#passCurrent').val(),
                    passNew: $('#passNew').val()
                })
            }).done(function () {
                track('password_reset', 'Authentication', 'PasswordReset');
                showMessage('#password-success', 'Password changed.', 'success');
                $('#frmPasswd')[0].reset();
            }).fail(function (xhr) {
                showMessage('#password-error', parseError(xhr, 'Unable to change password'));
            });
        });
    }

    function initContact() {
        var username = currentUsername();
        if (!username) {
            showMessage('#contact-error', 'You must be logged in to edit contact information.');
            return;
        }

        $.getJSON('/api/contact/' + encodeURIComponent(username)).done(function (contact) {
            $.each(contact || {}, function (key, value) {
                $('#frmContact').find('[name="' + key + '"]').val(value);
            });
        });

        $('#frmContact').on('submit', function (event) {
            event.preventDefault();
            showMessage('#contact-error', '');
            showMessage('#contact-success', '');

            var payload = {};
            $.each($(this).serializeArray(), function (_, field) {
                payload[field.name] = field.value;
            });

            $.ajax({
                url: '/api/contact',
                method: 'PUT',
                contentType: 'application/json',
                data: JSON.stringify(payload)
            }).done(function () {
                track('contact', 'Preferences', 'Contact');
                showMessage('#contact-success', 'Contact information updated successfully.', 'success');
            }).fail(function (xhr) {
                showMessage('#contact-error', parseError(xhr, 'Unable to save contact information'));
            });
        });
    }

    function initLinks() {
        var username = currentUsername();
        if (!username) {
            $('#frmAddLink').find('input, button').prop('disabled', true);
            showMessage('#links-error', 'You must be logged in to edit your links.');
            return;
        }

        function clearMessages() {
            showMessage('#links-error', '');
            showMessage('#links-success', '');
        }

        function linkRow(link) {
            var $row = $('<tr/>').attr('data-link-id', link.id);
            $row.append($('<td/>').append(
                $('<input type="text" class="form-control input-sm link-title" maxlength="50" required>').val(link.title)));
            $row.append($('<td/>').append(
                $('<input type="url" class="form-control input-sm link-uri" maxlength="255" required>').val(link.uri)));
            $row.append($('<td class="text-nowrap"/>')
                .append('<button type="button" class="btn btn-default btn-sm link-save"><i class="fa fa-save"></i> Save</button> ')
                .append('<button type="button" class="btn btn-danger btn-sm link-delete"><i class="fa fa-trash"></i> Delete</button>'));
            return $row;
        }

        function loadLinks() {
            $.getJSON('/api/link/user/' + encodeURIComponent(username)).done(function (links) {
                var $list = $('#links-list').empty();
                $.each(links || [], function (_, link) {
                    $list.append(linkRow(link));
                });
                $('#links-table').toggle($list.children().length > 0);
                $('#links-empty').toggle($list.children().length === 0);
            }).fail(function (xhr) {
                showMessage('#links-error', escapeHtml(parseError(xhr, 'Unable to load links')));
            });
        }

        loadLinks();

        $('#frmAddLink').on('submit', function (event) {
            event.preventDefault();
            clearMessages();

            $.ajax({
                url: '/api/link',
                method: 'PUT',
                contentType: 'application/json',
                data: JSON.stringify({
                    title: $('#link-title').val().trim(),
                    uri: $('#link-uri').val().trim()
                })
            }).done(function () {
                track('link_add', 'Preferences', 'Links');
                showMessage('#links-success', 'Link added.', 'success');
                $('#frmAddLink')[0].reset();
                loadLinks();
            }).fail(function (xhr) {
                showMessage('#links-error', escapeHtml(parseError(xhr, 'Unable to add link')));
            });
        });

        $('#links-list').on('click', '.link-save', function () {
            var $row = $(this).closest('tr');
            clearMessages();

            $.ajax({
                url: '/api/link/' + encodeURIComponent($row.attr('data-link-id')),
                method: 'PUT',
                contentType: 'application/json',
                data: JSON.stringify({
                    title: $row.find('.link-title').val().trim(),
                    uri: $row.find('.link-uri').val().trim()
                })
            }).done(function () {
                track('link_edit', 'Preferences', 'Links');
                showMessage('#links-success', 'Link updated.', 'success');
                loadLinks();
            }).fail(function (xhr) {
                showMessage('#links-error', escapeHtml(parseError(xhr, 'Unable to update link')));
            });
        });

        $('#links-list').on('click', '.link-delete', function () {
            var $row = $(this).closest('tr');
            clearMessages();

            if (!window.confirm('Delete the link "' + $row.find('.link-title').val() + '"?')) {
                return;
            }

            $.ajax({
                url: '/api/link/' + encodeURIComponent($row.attr('data-link-id')),
                method: 'DELETE'
            }).done(function () {
                track('link_delete', 'Preferences', 'Links');
                showMessage('#links-success', 'Link deleted.', 'success');
                loadLinks();
            }).fail(function (xhr) {
                showMessage('#links-error', escapeHtml(parseError(xhr, 'Unable to delete link')));
            });
        });
    }

    function initAvatar() {
        function showPreference(pref) {
            $('#avatar-show').prop('checked', pref.show === true).prop('disabled', pref.available !== true && pref.show !== true);
            $('#avatar-show-save').prop('disabled', pref.available !== true && pref.show !== true);
            $('#avatar-show-help').toggle(pref.available !== true);
        }

        function loadPreference() {
            $.getJSON('/Avatar/preference').done(showPreference).fail(function (xhr) {
                showMessage('#avatar-show-error', parseError(xhr, 'Unable to load avatar settings'));
            });
        }

        uploadForm('#avatar-form', '/Avatar', '#avatar-success', '#avatar-error', '#avatar-progress-container', '#avatar-progress', null, loadPreference);

        if (!currentUsername()) {
            return;
        }
        loadPreference();

        $('#avatar-show-form').on('submit', function (event) {
            event.preventDefault();
            showMessage('#avatar-show-error', '');
            showMessage('#avatar-show-success', '');

            $.ajax({
                url: '/Avatar/preference',
                method: 'PUT',
                contentType: 'application/json',
                data: JSON.stringify({show: $('#avatar-show').is(':checked')})
            }).done(function (pref) {
                track('avatar_show', 'Preferences', 'Avatar');
                showPreference(pref);
                showMessage('#avatar-show-success', 'Avatar setting saved.', 'success');
            }).fail(function (xhr) {
                showMessage('#avatar-show-error', parseError(xhr, 'Unable to save avatar setting'));
            });
        });
    }

    function initJournal() {
        var username = currentUsername();
        if (!username) {
            showMessage('#journal-error', 'You must be logged in to edit journal settings.');
            return;
        }

        $.when(
            $.getJSON('/api/styles'),
            $.getJSON('/api/journal/user/' + encodeURIComponent(username))
        ).done(function (stylesRes, journalsRes) {
            var styles = stylesRes[0] || [];
            var journals = journalsRes[0] || [];
            var journal = journals.length > 0 ? journals[0] : {};

            populateSelect($('#style'), styles, 'id', 'title', journal.styleId || (journal.style && journal.style.id));
            $('#name').val(journal.name || '');
            $('#allowSpider').prop('checked', journal.allowSpider === true);
            $('#pingServices').prop('checked', journal.pingServices === true);
            $('#ownerViewOnly').prop('checked', journal.ownerViewOnly === true);
            $('#frmJournal').data('journal', journal);
        }).fail(function (xhr) {
            showMessage('#journal-error', parseError(xhr, 'Unable to load journal settings'));
        });

        $('#frmJournal').on('submit', function (event) {
            event.preventDefault();
            showMessage('#journal-error', '');
            showMessage('#journal-success', '');

            var journal = $(this).data('journal') || {};
            var payload = {
                id: journal.id || 0,
                slug: journal.slug || username,
                name: $('#name').val().trim(),
                styleId: parseInt($('#style').val(), 10),
                allowSpider: $('#allowSpider').is(':checked'),
                pingServices: $('#pingServices').is(':checked'),
                ownerViewOnly: $('#ownerViewOnly').is(':checked')
            };

            $.ajax({
                url: '/api/journal',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(payload)
            }).done(function () {
                track('journal', 'Preferences', 'Journal');
                showMessage('#journal-success', 'Journal changed.', 'success');
            }).fail(function (xhr) {
                showMessage('#journal-error', parseError(xhr, 'Unable to save journal settings'));
            });
        });
    }

    function initDeleteAccount() {
        if (!currentUsername()) {
            $('#frmJournalDelete').find('input, button').prop('disabled', true);
            showMessage('#delete-account-error', 'You must be logged in to delete your account.');
            return;
        }

        $('#frmJournalDelete').on('submit', function (event) {
            event.preventDefault();
            showMessage('#delete-account-error', '');
            showMessage('#delete-account-success', '');

            if (!$('#confirm-delete').is(':checked')) {
                showMessage('#delete-account-error', 'Please confirm that you want to permanently delete your account.');
                return;
            }

            $.ajax({
                url: '/api/account',
                method: 'DELETE'
            }).done(function () {
                track('account_delete', 'Preferences', 'AccountDelete');
                showMessage('#delete-account-success', 'Account deleted.', 'success');
                window.setTimeout(function () {
                    window.location.href = '/';
                }, 1000);
            }).fail(function (xhr) {
                showMessage('#delete-account-error', parseError(xhr, 'Unable to delete account'));
            });
        });
    }

    function initPageView() {
        var pageId = pageData('page') || window.location.pathname;
        track('page_view', pageId, pageId);
    }

    $(function () {
        initPageView();

        switch (pageData('page')) {
            case 'home':
                initHome();
                break;
            case 'members':
                initMembers();
                break;
            case 'moodlist':
                initMoodList();
                break;
            case 'search':
                initSearch();
                break;
            case 'stats':
                initStats();
                break;
            case 'tags':
                initTags();
                break;
            case 'profile':
                initProfile();
                break;
            case 'entry':
                initEntry();
                break;
            case 'comment':
                initComment();
                break;
            case 'prefs-avatar':
                initAvatar();
                break;
            case 'prefs-image':
                uploadForm('#image-form', '/AlbumImage', '#image-success', '#image-error', '#image-progress-container', '#image-progress');
                break;
            case 'prefs-biography':
                initBiography();
                break;
            case 'prefs-password':
                initPassword();
                break;
            case 'prefs-contact':
                initContact();
                break;
            case 'prefs-links':
                initLinks();
                break;
            case 'prefs-journal':
                initJournal();
                break;
            case 'prefs-journaldelete':
                initDeleteAccount();
                break;
            default:
                break;
        }
    });
})(jQuery);
