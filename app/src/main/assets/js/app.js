var pendingCallbacks = {};
var mediaData = null;
var currentPlatform = 'Youtube';
var currentTab = 'audio';
var currentPreviewFile = null;
var selectMode = false;
var selectedKeys = {};
var downloadBusy = false;
var lastYoutubePromptUrl = '';

function makeRequestId() {
    return 'r' + Date.now() + Math.floor(Math.random() * 10000);
}

function $(id) {
    return document.getElementById(id);
}

function showPage(id) {
    var pages = document.querySelectorAll('.page');
    var buttons = document.querySelectorAll('.drawer-item[data-page]');
    for (var i = 0; i < pages.length; i++) pages[i].classList.remove('active');
    $(id).classList.add('active');
    for (var j = 0; j < buttons.length; j++) {
        buttons[j].classList.toggle('active', buttons[j].getAttribute('data-page') === id);
    }
    closeDrawer();
    if (id === 'page-media') loadMediaList();
    if (id === 'page-settings') refreshBackgroundState();
}

function openDrawer() {
    $('drawer').classList.add('open');
    $('drawer-backdrop').classList.add('open');
}

function closeDrawer() {
    $('drawer').classList.remove('open');
    $('drawer-backdrop').classList.remove('open');
}

function showStatus(text, error) {
    var box = $('status-box');
    box.textContent = text;
    box.classList.remove('hidden', 'error');
    if (error) box.classList.add('error');
}

function hideStatus() {
    $('status-box').classList.add('hidden');
}

function setDownloadLoading(active) {
    downloadBusy = active;
    $('btn-download').disabled = active;
    $('btn-download').classList.toggle('busy', active);
    $('btn-download').querySelector('.download-icon').classList.toggle('hidden', active);
    $('btn-download').querySelector('.download-loading').classList.toggle('hidden', !active);
}

function extractYoutubeId(url) {
    if (!url) return null;
    var match = String(url).match(/(?:youtu\.be\/|youtube\.com\/(?:embed\/|live\/|shorts\/)|[?&]v=)([a-zA-Z0-9-_]{11})/);
    return match ? match[1] : null;
}

function isYoutubeUrl(url) {
    return !!extractYoutubeId((url || '').trim());
}

function isTiktokUrl(url) {
    url = (url || '').toLowerCase();
    return url.indexOf('tiktok.com/') !== -1 || url.indexOf('vt.tiktok.com/') !== -1 || url.indexOf('vm.tiktok.com/') !== -1;
}

function detectPlatform(url) {
    if (isYoutubeUrl(url)) return 'Youtube';
    if (isTiktokUrl(url)) return 'Tiktok';
    return '';
}

function updateHomePlatform(url) {
    var platform = detectPlatform(url);
    var icon = $('home-platform-icon');
    var title = $('hero-title');
    var subtitle = $('hero-subtitle');
    var label = $('home-platform-label');

    if (platform === 'Youtube') {
        icon.innerHTML = '<i class="fa-brands fa-youtube"></i>';
        title.textContent = 'download youtube';
        subtitle.textContent = 'pilih mp3 atau mp4 setelah link terdeteksi';
        label.textContent = 'youtube';
    } else if (platform === 'Tiktok') {
        icon.innerHTML = '<i class="fa-brands fa-tiktok"></i>';
        title.textContent = 'download tiktok';
        subtitle.textContent = 'tempel link, lalu download';
        label.textContent = 'tiktok';
    } else {
        icon.innerHTML = '<i class="fa-solid fa-arrow-down"></i>';
        title.textContent = 'download media';
        subtitle.textContent = 'tempel link tiktok atau youtube';
        label.textContent = 'tiktok + youtube';
    }
}

function showYoutubeFormatModal(url) {
    url = (url || '').trim();
    if (!isYoutubeUrl(url)) return;
    lastYoutubePromptUrl = url;
    $('youtube-format-url').textContent = url.length > 72 ? url.slice(0, 69) + '...' : url;
    $('youtube-format-modal').classList.remove('hidden');
}

function hideYoutubeFormatModal() {
    $('youtube-format-modal').classList.add('hidden');
}

function downloadUrl(url, format) {
    url = (url || '').trim();
    if (!url) {
        showStatus('masukin link tiktok atau youtube dulu', true);
        return;
    }
    if (downloadBusy) return;

    var platform = detectPlatform(url);
    if (!platform) {
        showStatus('link belum dikenali sebagai tiktok atau youtube', true);
        return;
    }
    if (platform === 'Youtube' && !format) {
        showYoutubeFormatModal(url);
        return;
    }
    if (!Android.hasStoragePermission()) {
        showStatus('izin penyimpanan belum aktif, buka pengaturan dulu', true);
        Android.requestStoragePermission();
        return;
    }

    $('result-box').classList.add('hidden');
    if (platform === 'Youtube') showStatus('sedang menyiapkan youtube ' + format + '...', false);
    else showStatus('sedang mengambil media tiktok...', false);
    setDownloadLoading(true);

    var reqId = makeRequestId();
    pendingCallbacks[reqId] = function(result) {
        setDownloadLoading(false);
        if (result) renderResult(result);
    };
    Android.downloadMedia(platform, url, format || '', reqId);
}

$('btn-menu').addEventListener('click', openDrawer);
$('btn-media-menu').addEventListener('click', openDrawer);
$('btn-settings-menu').addEventListener('click', openDrawer);
$('drawer-backdrop').addEventListener('click', closeDrawer);

var drawerItems = document.querySelectorAll('.drawer-item[data-page]');
for (var i = 0; i < drawerItems.length; i++) {
    drawerItems[i].addEventListener('click', function() {
        showPage(this.getAttribute('data-page'));
    });
}

$('btn-home-media').addEventListener('click', function() { showPage('page-media'); });
$('btn-quick-background').addEventListener('click', function() { showPage('page-settings'); });

$('btn-paste').addEventListener('click', function() {
    var reqId = makeRequestId();
    pendingCallbacks[reqId] = function(result) {
        if (result.text) {
            $('input-url').value = result.text.trim();
            updateHomePlatform(result.text);
            $('input-url').focus();
            showStatus('link ditempel dari clipboard', false);
            if (isYoutubeUrl(result.text)) setTimeout(function() { showYoutubeFormatModal(result.text); }, 80);
        } else {
            showStatus('clipboard kosong', true);
        }
    };
    Android.getClipboardText(reqId);
});

$('btn-download').addEventListener('click', function() {
    var url = $('input-url').value;
    if (isYoutubeUrl(url)) showYoutubeFormatModal(url);
    else downloadUrl(url, null);
});

$('input-url').addEventListener('input', function() {
    var value = this.value.trim();
    hideStatus();
    updateHomePlatform(value);
    if (isYoutubeUrl(value) && value !== lastYoutubePromptUrl) {
        setTimeout(function() {
            if ($('input-url').value.trim() === value) showYoutubeFormatModal(value);
        }, 120);
    }
    if (!isYoutubeUrl(value)) lastYoutubePromptUrl = '';
});

$('btn-youtube-mp3').addEventListener('click', function() {
    var url = $('input-url').value.trim() || lastYoutubePromptUrl;
    hideYoutubeFormatModal();
    downloadUrl(url, 'mp3');
});
$('btn-youtube-mp4').addEventListener('click', function() {
    var url = $('input-url').value.trim() || lastYoutubePromptUrl;
    hideYoutubeFormatModal();
    downloadUrl(url, 'mp4');
});
$('btn-youtube-cancel').addEventListener('click', hideYoutubeFormatModal);
$('youtube-format-modal').addEventListener('click', function(e) {
    if (e.target === $('youtube-format-modal')) hideYoutubeFormatModal();
});

$('btn-drawer-theme').addEventListener('click', function() {
    var enabled = !document.body.classList.contains('dark');
    document.body.classList.toggle('dark', enabled);
    localStorage.setItem('darkMode', enabled ? '1' : '0');
    updateThemeLabel();
});

function updateThemeLabel() {
    $('theme-state').textContent = document.body.classList.contains('dark') ? 'on' : 'off';
}

if (localStorage.getItem('darkMode') === '1') document.body.classList.add('dark');
updateThemeLabel();

function renderResult(result) {
    hideStatus();
    var filesBox = $('result-files');
    var metaBox = $('result-meta');
    filesBox.innerHTML = '';
    metaBox.innerHTML = '';

    if (result.alreadyExists) {
        $('result-title').textContent = 'sudah pernah didownload';
        metaBox.textContent = 'file dari link ini sudah ada di penyimpanan aplikasi.';
    } else {
        $('result-title').textContent = result.platform === 'Youtube' ? 'youtube selesai' : 'tiktok selesai';
        if (result.meta) {
            var meta = result.meta;
            var html = '';
            if (meta.title) html += '<div><b>judul:</b> ' + escapeHtml(meta.title) + '</div>';
            if (meta.uploader) html += '<div><b>akun:</b> ' + escapeHtml(meta.uploader) + '</div>';
            if (meta.duration) html += '<div><b>durasi:</b> ' + meta.duration + ' detik</div>';
            if (meta.playCount) html += '<div><b>views:</b> ' + meta.playCount + '</div>';
            if (result.format) html += '<div><b>format:</b> ' + escapeHtml(result.format) + '</div>';
            metaBox.innerHTML = html || 'metadata tidak tersedia.';
        } else if (result.format) {
            metaBox.innerHTML = '<div><b>platform:</b> youtube</div><div><b>format:</b> ' + escapeHtml(result.format) + '</div>';
        }
    }

    var files = result.files || [];
    for (var i = 0; i < files.length; i++) filesBox.appendChild(buildFileCard(files[i]));
    $('result-box').classList.remove('hidden');
}

function buildFileCard(file) {
    var row = document.createElement('div');
    row.className = 'file-card';

    var icon = document.createElement('i');
    icon.className = iconForKind(file.kind);
    row.appendChild(icon);

    var main = document.createElement('div');
    main.className = 'file-main';
    var name = document.createElement('div');
    name.className = 'file-name';
    name.textContent = file.name;
    var size = document.createElement('div');
    size.className = 'file-size';
    size.textContent = formatSize(file.size);
    main.appendChild(name);
    main.appendChild(size);
    row.appendChild(main);

    if (file.kind === 'video' || file.kind === 'audio' || file.kind === 'image') {
        var play = document.createElement('button');
        play.className = 'file-action';
        play.innerHTML = '<i class="fa-solid fa-play"></i>';
        play.addEventListener('click', function() { openPreview(file, 'page-home'); });
        row.appendChild(play);
    }

    var share = document.createElement('button');
    share.className = 'file-action';
    share.innerHTML = '<i class="fa-solid fa-share-nodes"></i>';
    share.addEventListener('click', function() { Android.shareFile(file.path); });
    row.appendChild(share);
    return row;
}

function iconForKind(kind) {
    if (kind === 'video') return 'fa-solid fa-file-video';
    if (kind === 'audio') return 'fa-solid fa-file-audio';
    if (kind === 'image') return 'fa-solid fa-file-image';
    return 'fa-solid fa-file';
}

function iconColor(kind) {
    if (kind === 'video') return 'fa-solid fa-video';
    if (kind === 'audio') return 'fa-solid fa-music';
    if (kind === 'image') return 'fa-solid fa-image';
    return 'fa-solid fa-file';
}

function escapeHtml(value) {
    var div = document.createElement('div');
    div.textContent = value == null ? '' : String(value);
    return div.innerHTML;
}

function openPreview(file, backPage) {
    currentPreviewFile = file;
    $('preview-title').textContent = file.name;
    var content = $('preview-content');
    content.innerHTML = '';
    content.setAttribute('data-back-page', backPage || 'page-media');

    var src = 'file://' + file.path;
    if (file.kind === 'video') {
        var video = document.createElement('video');
        video.src = src;
        video.controls = true;
        video.autoplay = true;
        video.playsInline = true;
        content.appendChild(video);
    } else if (file.kind === 'audio') {
        var audio = document.createElement('audio');
        audio.src = src;
        audio.controls = true;
        audio.autoplay = true;
        content.appendChild(audio);
    } else if (file.kind === 'image') {
        var img = document.createElement('img');
        img.src = src;
        content.appendChild(img);
    }
    showPage('page-preview');
}

$('back-from-preview').addEventListener('click', function() {
    $('preview-content').innerHTML = '';
    showPage($('preview-content').getAttribute('data-back-page') || 'page-media');
});
$('btn-share-preview').addEventListener('click', function() {
    if (currentPreviewFile) Android.shareFile(currentPreviewFile.path);
});

function updateMediaPlatformButtons() {
    var buttons = document.querySelectorAll('.media-platform-tab[data-platform]');
    for (var i = 0; i < buttons.length; i++) {
        buttons[i].classList.toggle('active', buttons[i].getAttribute('data-platform') === currentPlatform);
    }

    var foto = $('tab-foto');
    if (currentPlatform === 'Youtube') {
        foto.classList.add('hidden');
        if (currentTab === 'foto') currentTab = 'audio';
    } else {
        foto.classList.remove('hidden');
    }
    updateMediaTypeButtons();
}

function updateMediaTypeButtons() {
    var buttons = document.querySelectorAll('.media-tab[data-tab]');
    for (var i = 0; i < buttons.length; i++) {
        buttons[i].classList.toggle('active', buttons[i].getAttribute('data-tab') === currentTab);
    }
}

var mediaPlatformButtons = document.querySelectorAll('.media-platform-tab[data-platform]');
for (var mp = 0; mp < mediaPlatformButtons.length; mp++) {
    mediaPlatformButtons[mp].addEventListener('click', function() {
        var platform = this.getAttribute('data-platform');
        if (platform === currentPlatform) return;
        currentPlatform = platform;
        selectedKeys = {};
        selectMode = false;
        $('select-bar').classList.add('hidden');
        $('btn-select-mode').innerHTML = '<i class="fa-solid fa-check-double"></i> pilih';
        updateMediaPlatformButtons();
        loadMediaList();
    });
}

var mediaTabButtons = document.querySelectorAll('.media-tab[data-tab]');
for (var mt = 0; mt < mediaTabButtons.length; mt++) {
    mediaTabButtons[mt].addEventListener('click', function() {
        var tab = this.getAttribute('data-tab');
        if (tab === 'foto' && currentPlatform === 'Youtube') return;
        currentTab = tab;
        selectedKeys = {};
        selectMode = false;
        $('select-bar').classList.add('hidden');
        $('btn-select-mode').innerHTML = '<i class="fa-solid fa-check-double"></i> pilih';
        updateMediaTypeButtons();
        renderMediaList();
    });
}

$('btn-refresh-media').addEventListener('click', loadMediaList);
$('btn-select-mode').addEventListener('click', function() {
    selectMode = !selectMode;
    selectedKeys = {};
    $('select-bar').classList.toggle('hidden', !selectMode);
    $('btn-select-mode').innerHTML = selectMode ? '<i class="fa-solid fa-xmark"></i> batal' : '<i class="fa-solid fa-check-double"></i> pilih';
    renderMediaList();
});

function loadMediaList() {
    $('media-list-content').innerHTML = '<div class="empty-state"><i class="fa-solid fa-spinner fa-spin"></i><span>memuat media...</span></div>';
    var reqId = makeRequestId();
    pendingCallbacks[reqId] = function(data) {
        mediaData = data;
        updateMediaPlatformButtons();
        renderMediaList();
    };
    Android.getMediaList(reqId);
}

function getAllFiles() {
    var files = [];
    var platforms = mediaData && mediaData.platforms ? mediaData.platforms : [];
    for (var i = 0; i < platforms.length; i++) {
        var p = platforms[i];
        if (p.platform !== currentPlatform) continue;
        var items = p.items || [];
        for (var j = 0; j < items.length; j++) {
            var item = items[j];
            var itemFiles = item.files || [];
            for (var k = 0; k < itemFiles.length; k++) {
                var f = itemFiles[k];
                files.push({
                    platform: p.platform,
                    id: item.id,
                    path: f.path,
                    name: f.name,
                    size: f.size,
                    kind: f.kind,
                    thumbnail: f.thumbnail || '',
                    lastModified: f.lastModified || 0
                });
            }
        }
    }
    return files;
}

function renderMediaList() {
    var all = getAllFiles();
    var targetKind = currentTab === 'video' ? 'video' : (currentTab === 'foto' ? 'image' : 'audio');
    all = all.filter(function(file) { return file.kind === targetKind; });
    all.sort(function(a, b) { return (b.lastModified || 0) - (a.lastModified || 0); });
    $('media-count').textContent = all.length + ' file';

    var container = $('media-list-content');
    if (!all.length) {
        var label = currentTab === 'foto' ? 'foto' : currentTab;
        container.innerHTML = '<div class="empty-state"><i class="fa-solid fa-folder-open"></i><span>belum ada ' + label + ' ' + currentPlatform.toLowerCase() + '</span></div>';
        return;
    }

    container.innerHTML = '';
    if (currentTab === 'video' || currentTab === 'foto') {
        var grid = document.createElement('div');
        grid.className = 'thumb-grid';
        for (var i = 0; i < all.length; i++) grid.appendChild(buildThumbItem(all[i]));
        container.appendChild(grid);
    } else {
        for (var j = 0; j < all.length; j++) container.appendChild(buildAudioRow(all[j]));
    }
}

function buildThumbItem(file) {
    var item = document.createElement('div');
    item.className = 'thumb-item';

    if (file.kind === 'video' && file.thumbnail) {
        var image = document.createElement('img');
        image.className = 'thumb-img';
        image.src = file.thumbnail;
        item.appendChild(image);
    } else if (file.kind === 'image') {
        var photo = document.createElement('img');
        photo.className = 'thumb-img';
        photo.src = 'file://' + file.path;
        item.appendChild(photo);
    } else {
        var placeholder = document.createElement('div');
        placeholder.className = 'thumb-placeholder';
        placeholder.innerHTML = '<i class="' + iconColor(file.kind) + '"></i>';
        item.appendChild(placeholder);
    }

    var overlay = document.createElement('div');
    overlay.className = 'thumb-overlay';
    var label = document.createElement('div');
    label.className = 'thumb-name';
    label.textContent = file.name;
    overlay.appendChild(label);
    item.appendChild(overlay);

    if (selectMode) {
        var cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.className = 'thumb-check';
        cb.checked = !!selectedKeys[file.path];
        cb.addEventListener('click', function(e) { e.stopPropagation(); });
        cb.addEventListener('change', function() {
            if (cb.checked) selectedKeys[file.path] = true;
            else delete selectedKeys[file.path];
        });
        item.appendChild(cb);
    } else {
        var actions = document.createElement('div');
        actions.className = 'thumb-actions';
        var share = document.createElement('button');
        share.className = 'thumb-action';
        share.innerHTML = '<i class="fa-solid fa-share-nodes"></i>';
        share.addEventListener('click', function(e) { e.stopPropagation(); Android.shareFile(file.path); });
        var del = document.createElement('button');
        del.className = 'thumb-action';
        del.innerHTML = '<i class="fa-solid fa-trash"></i>';
        del.addEventListener('click', function(e) {
            e.stopPropagation();
            if (!confirm('hapus file ini?')) return;
            var reqId = makeRequestId();
            pendingCallbacks[reqId] = function() { loadMediaList(); };
            Android.deleteFile(file.path, reqId);
        });
        actions.appendChild(share);
        actions.appendChild(del);
        item.appendChild(actions);
    }

    item.addEventListener('click', function() {
        if (!selectMode) openPreview(file, 'page-media');
    });
    return item;
}

function buildAudioRow(file) {
    var row = document.createElement('div');
    row.className = 'list-row';
    var icon = document.createElement('i');
    icon.className = iconForKind(file.kind) + ' list-icon';
    row.appendChild(icon);

    var main = document.createElement('div');
    main.className = 'list-main';
    var title = document.createElement('div');
    title.className = 'list-title';
    title.textContent = file.name;
    var sub = document.createElement('div');
    sub.className = 'list-sub';
    sub.textContent = formatSize(file.size);
    main.appendChild(title);
    main.appendChild(sub);
    row.appendChild(main);

    if (selectMode) {
        var cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.checked = !!selectedKeys[file.path];
        cb.addEventListener('change', function() {
            if (cb.checked) selectedKeys[file.path] = true;
            else delete selectedKeys[file.path];
        });
        row.appendChild(cb);
    } else {
        var actions = document.createElement('div');
        actions.className = 'list-actions';
        var play = document.createElement('button');
        play.className = 'list-action';
        play.innerHTML = '<i class="fa-solid fa-play"></i>';
        play.addEventListener('click', function(e) { e.stopPropagation(); Android.openAudioExternal(file.path); });
        var share = document.createElement('button');
        share.className = 'list-action';
        share.innerHTML = '<i class="fa-solid fa-share-nodes"></i>';
        share.addEventListener('click', function(e) { e.stopPropagation(); Android.shareFile(file.path); });
        var del = document.createElement('button');
        del.className = 'list-action';
        del.innerHTML = '<i class="fa-solid fa-trash"></i>';
        del.addEventListener('click', function(e) {
            e.stopPropagation();
            if (!confirm('hapus file ini?')) return;
            var reqId = makeRequestId();
            pendingCallbacks[reqId] = function() { loadMediaList(); };
            Android.deleteFile(file.path, reqId);
        });
        actions.appendChild(play);
        actions.appendChild(share);
        actions.appendChild(del);
        row.appendChild(actions);
        row.addEventListener('click', function() { Android.openAudioExternal(file.path); });
    }
    return row;
}

$('btn-select-all').addEventListener('click', function() {
    var all = getAllFiles();
    var targetKind = currentTab === 'video' ? 'video' : (currentTab === 'foto' ? 'image' : 'audio');
    all = all.filter(function(file) { return file.kind === targetKind; });
    var every = all.length > 0 && all.every(function(file) { return !!selectedKeys[file.path]; });
    selectedKeys = {};
    if (!every) {
        for (var i = 0; i < all.length; i++) selectedKeys[all[i].path] = true;
    }
    renderMediaList();
});

$('btn-delete-selected').addEventListener('click', function() {
    var paths = Object.keys(selectedKeys);
    if (!paths.length) {
        alert('belum ada file yang dipilih');
        return;
    }
    if (!confirm('hapus ' + paths.length + ' file terpilih?')) return;
    var reqId = makeRequestId();
    pendingCallbacks[reqId] = function() {
        selectedKeys = {};
        selectMode = false;
        $('select-bar').classList.add('hidden');
        $('btn-select-mode').innerHTML = '<i class="fa-solid fa-check-double"></i> pilih';
        loadMediaList();
    };
    Android.deleteFiles(JSON.stringify(paths), reqId);
});

function formatSize(bytes) {
    if (!bytes) return '0 B';
    var units = ['B', 'KB', 'MB', 'GB'];
    var i = 0;
    var value = Number(bytes);
    while (value >= 1024 && i < units.length - 1) {
        value /= 1024;
        i++;
    }
    return value.toFixed(1) + ' ' + units[i];
}

function refreshBackgroundState() {
    var reqId = makeRequestId();
    pendingCallbacks[reqId] = function(state) {
        var running = !!state.running;
        var mode = state.mode || '';
        $('service-dot').classList.toggle('on', running);
        $('btn-stop-service').classList.toggle('hidden', !running);
        $('btn-mode-popup').classList.toggle('active', running && mode === 'popup');
        $('btn-mode-notification').classList.toggle('active', running && mode === 'notification');
        $('overlay-status').textContent = state.overlayAllowed ? 'diizinkan' : 'belum diizinkan';
        $('battery-status').textContent = state.batteryOptimizedOff ? 'tidak dibatasi' : 'masih dioptimasi';
        $('quick-background-status').textContent = running ? ('aktif: ' + mode) : 'atur popup atau notifikasi di background';
    };
    Android.getBackgroundState(reqId);
}

$('btn-mode-popup').addEventListener('click', function() {
    Android.startBackgroundService('popup');
    setTimeout(refreshBackgroundState, 350);
});
$('btn-mode-notification').addEventListener('click', function() {
    Android.startBackgroundService('notification');
    setTimeout(refreshBackgroundState, 350);
});
$('btn-stop-service').addEventListener('click', function() {
    Android.stopBackgroundService();
    setTimeout(refreshBackgroundState, 350);
});
$('btn-vivo-settings').addEventListener('click', function() { Android.openVivoBackgroundSettings(); });
$('btn-battery-settings').addEventListener('click', function() { Android.openBatterySettings(); });
$('btn-app-settings').addEventListener('click', function() { Android.openAppDetails(); });
$('btn-overlay-settings').addEventListener('click', function() { Android.openOverlaySettings(); });

function onDownloadSuccess(result) {
    var cb = pendingCallbacks[result.requestId];
    delete pendingCallbacks[result.requestId];
    if (cb) cb(result);
}

function onDownloadError(err) {
    var cb = pendingCallbacks[err.requestId];
    delete pendingCallbacks[err.requestId];
    if (cb) cb(null);
    setDownloadLoading(false);
    showStatus('gagal: ' + err.message, true);
}

function onMediaList(data) {
    var cb = pendingCallbacks[data.requestId];
    delete pendingCallbacks[data.requestId];
    if (cb) cb(data);
}

function onClipboardText(data) {
    var cb = pendingCallbacks[data.requestId];
    delete pendingCallbacks[data.requestId];
    if (cb) cb(data);
}

function onBackgroundState(data) {
    var cb = pendingCallbacks[data.requestId];
    delete pendingCallbacks[data.requestId];
    if (cb) cb(data);
}

function onMediaDeleted(data) { finishSimple(data); }
function onPlatformDeleted(data) { finishSimple(data); }
function onAllDeleted(data) { finishSimple(data); }
function onFileDeleted(data) { finishSimple(data); }
function onFilesDeleted(data) { finishSimple(data); }
function onFileRenamed(data) { finishSimple(data); }
function finishSimple(data) {
    var cb = pendingCallbacks[data.requestId];
    delete pendingCallbacks[data.requestId];
    if (cb) cb(data);
}

updateMediaPlatformButtons();
updateHomePlatform('');
refreshBackgroundState();
