"use strict"

//-----------------------------------------------------------------------------
// Data control extensions.
//-----------------------------------------------------------------------------

mdc.dataTable.MDCDataTable.prototype.clearRows = function() {
    this.content.innerHTML = '';
    
    //
    // Update internal bindings.
    //
    this.layout();
};

mdc.dataTable.MDCDataTable.prototype.addRow = function(id, columns, showCheckbox=true) {
    console.assert(id);
    console.assert(columns);

    const tr = $(`<tr data-row-id="${id}" class="mdc-data-table__row"></tr>`);
    $(this.content).append(tr);

    if (showCheckbox) {
        const checkboxTd = $(`<td class="mdc-data-table__cell mdc-data-table__cell--checkbox">
                <div class="mdc-checkbox mdc-data-table__row-checkbox">
                    <input type="checkbox" class="mdc-checkbox__native-control"/>
                    <div class="mdc-checkbox__background">
                    <svg class="mdc-checkbox__checkmark" viewBox="0 0 24 24">
                        <path class="mdc-checkbox__checkmark-path" fill="none" d="M1.73,12.91 8.1,19.28 22.79,4.59" />
                    </svg>
                    <div class="mdc-checkbox__mixedmark"></div>
                </div>
                <div class="mdc-checkbox__ripple"></div>
                </div>
            </td>`);
        tr.append(checkboxTd);
    }

    let first = true;
    columns.forEach((value) => {
        const div = $("<div></div>");

        if (value.text && value.maxLength && value.text.length > value.maxLength) {
            div.prop('title', value.text);
            div.text(value.text.substring(0, value.maxLength) + '...');
        }
        else {
            div.text(value.text);
        }

        if (value.class) {
            div.attr("class", value.class);
        }

        let td;
        if (first) {
            td = $(`<th class="mdc-data-table__cell" scope="row" id="${id}"></th>`);
            first = false;
        }
        else {
            td = $(`<td class="mdc-data-table__cell"></td>`);
        }

        if (value.icon) {
            const icon = $("<span class='material-symbols-outlined'></span>");
            icon.text(value.icon);
            div.prepend(icon);
        }

        if (value.href) {
            const a = $("<a></a>");
            a.prop('href', value.href);
            a.append(div);
            td.append(a);
        }
        else {
            td.append(div);
        }
        
        tr.append(td);
    });

    //
    // Update internal bindings.
    //
    this.layout();
};

mdc.list.MDCList.prototype.clearRows = function () {
    this.root.innerHTML = '';

    //
    // Update internal bindings.
    //
    this.layout();
};


mdc.list.MDCList.prototype.addRow = function (column) {
    const li = $(`<li class="mdc-list-item">
        <span class="mdc-list-item__ripple"></span>
      </li>`);

    const textSpan = $(`<span class="mdc-list-item__text">`);

    if (column.icon) {
        const icon = $("<span class='material-symbols-outlined'></span>");
        icon.text(column.icon);
        textSpan.prepend(icon);
    }

    li.append(textSpan);

    if (column.key) {
        const span = $(`<span class="mdc-list-item__primary-text" style="display: inline-block; width: 150px"></span>`);
        if (column.key.length > 20) {
            span.text(column.key.substring(0, 20) + '...');
        }
        else {
            span.text(column.key)
        }
        textSpan.append(span);
    }
    if (column.value) {
        const span = $(`<span class="mdc-list-item__primary-text" style="display: inline-block;"></span>`);
        span.text(column.value)
        textSpan.append(span);
    }

    if (column.primary) {
        const span = $(`<span class="mdc-list-item__primary-text"></span>`);
        span.text(column.primary)
        textSpan.append(span);
    }
    if (column.secondary) {
        const span = $(`<span class="mdc-list-item__secondary-text"></span>`);
        span.text(column.secondary);
        textSpan.append(span);
    }

    $(this.root).append(li);

    //
    // Update internal bindings.
    //
    this.layout();
}

//-----------------------------------------------------------------------------
// Utility classes.
//-----------------------------------------------------------------------------

class Duration {
    constructor(days, hours, minutes, seconds) {
        this.days = days + Math.floor(hours / 24);
        this.hours = hours % 24;
        this.minutes = minutes;
        this.seconds = seconds;
    }

    /**
     * Parse a ISO8601 duration.
     */
    static parse(durationString) {
        const match = durationString.match(/^P((\d+)D)?(T((\d+)H)?((\d+)M)?((\d+)S)?)?$/);
        if (!match) {
            throw new Error($`'${durationString}'' is not a ISO 8601-formatted duration`);
        }

        return new Duration(
            parseInt(match[2] || 0),
            parseInt(match[5] || 0),
            parseInt(match[7] || 0),
            parseInt(match[9] || 0));
    }

    /**
     * Create Duration from a total number of seconds.
     */
    static fromSeconds(totalSeconds) {
        return new Duration(
            Math.floor(totalSeconds / (24 * 60 * 60)),
            Math.floor((totalSeconds % (24 * 60 * 60)) / (60 * 60)),
            Math.floor((totalSeconds % (60 * 60)) / 60),
            totalSeconds % 60);
    }

    /**
     * Create Duration for the time between now and a UNIX time stamp.
     */
    static untilUnixTime(unixTime) {
        return Duration.fromSeconds(unixTime - (new Date().getTime() / 1000));
    }

    static _pluralize(num, unit) {
        return `${num} ${unit}${num === 1 ? '' : 's'}`;
    }

    /**
     * Return a formatted string like '30 days, 12 hours, 1 minute'.
     */
    format() {
        let parts = [];
        if (this.days > 0) {
            parts.push(Duration._pluralize(this.days, "day"));
        }

        if (this.hours > 0) {
            parts.push(Duration._pluralize(this.hours, "hour"));
        }

        if (this.minutes > 0) {
            parts.push(Duration._pluralize(this.minutes, "minute"));
        }

        return parts.join(', ');
    }

    /**
     * Return the ISO8601 representation.
     */
    toString() {
        return `P${this.days}DT${this.hours}H${this.minutes}M${this.seconds}S`
    }

    /**
     * Total number of seconds.
     */
    toSeconds() {
        return (this.days * 24 * 60 * 60) +
            (this.hours * 60 * 60) +
            (this.minutes * 60) +
            this.seconds;
    }

    /**
     * Return a common durations between a lower and upper bound.
     */
    static range(minDuration, maxDuration) {
        console.assert(minDuration instanceof Duration);
        console.assert(minDuration instanceof Duration);

        const steps = [
            // minutes
            60,
            2 * 60,
            5 * 60,
            10 * 60,
            15 * 60,
            30 * 60,

            // hours
            1 * 60 * 60,
            2 * 60 * 60,
            3 * 60 * 60,
            6 * 60 * 60,
            12 * 60 * 60,

            // days
            1 * 24 * 60 * 60,
            7 * 24 * 60 * 60,
            30 * 24 * 60 * 60,
            60 * 24 * 60 * 60,
            90 * 24 * 60 * 60,
            180 * 24 * 60 * 60,
            365 * 24 * 60 * 60
        ];

        const intermediateDurations = steps
            .filter(s => 
                (minDuration === null || s > minDuration.toSeconds()) &&
                (maxDuration === null || s < maxDuration.toSeconds()))
            .map(s => Duration.fromSeconds(s));

        return [minDuration, ...intermediateDurations, maxDuration].filter(d => d !== null);
    }
}

//-----------------------------------------------------------------------------
// Base classes.
//-----------------------------------------------------------------------------

/** 
 * Base class for modal dialogs 
 */
class DialogBase {
    constructor(selector) {
        this.selector = selector;
        this.element = new mdc.dialog.MDCDialog(document.querySelector(selector));
    }

    /** return the dialog result */
    get result() {
        return null;
    }

    /** show dialog and await result */
    showAsync() {
        return new Promise((resolve, reject) => {
            this.element.listen('MDCDialog:closed', e => {
                if (e.detail.action == "accept") {
                    resolve(this.result);
                }
                else {
                    reject();
                }
            });

            this.cancelDialog = error => {
                this.element.close();
                reject(error);
            }

            this.element.open();
        });
    }

    select(relativeSelector) {
        return $(this.selector + ' ' + relativeSelector);
    }

    close() {
        this.element.close();
    }
}

/**
 * Base class for views 
 */
class ViewBase {
    constructor(selector) {
        this.selector = selector;
    }

    /** Show and hide all other views. */
    async showAsync() {
        document.appbar.clearError();
        $('.jit-view').hide();
        $(this.selector).show();
        
        return Promise.resolve({});
    }

    /**
     * Return a JQuery element relative to the current view's selector.
     */
    select(relativeSelector) {
        return $(this.selector + ' ' + relativeSelector);
    }

    /**
     * Hide current view and show an error instead.
     */
    cancelView(error) {
        $('.jit-view').hide();
        document.appbar.showError(error, true);
    }
}

class DefaultView extends ViewBase {
    constructor() {
        super('#jit-default-view');
    }
}

/** 
 * Dialog for selecting a scope
 */
class SelectScopeDialog extends DialogBase {
    constructor() {
        super('#jit-scopedialog');

        this._list = new mdc.list.MDCList(document.querySelector('#jit-scopedialog-list'));
    }


    async showAsync() {
        this._list.clearRows();

        const environments = await document.appbar.model.listEnvironments();

        if (environments.environments.length > 0) {
            environments.environments.forEach(item => {
                this._list.addRow({
                    primary: item.name,
                    secondary: item.description
                });
            });
        }
        else {
            throw "There are currently no environments available"
        }

        const dialog = this.element;
        let onSelect = (e) => {
            this._list.unlisten('MDCList:action', onSelect);

            this._result = environments.environments[e.detail.index].name;

            dialog.close("accept");
        }

        this._list.listen('MDCList:action', onSelect);        

        return super.showAsync();
    }

    get result() {
        return this._result;
    }
}

/** 
 * App bar at top of screen 
 */
class AppBar {
    constructor() {
        this._banner = new mdc.banner.MDCBanner(document.querySelector('.mdc-banner'));
        
        $('#jit-environmentselector').on('click', () => {
            this.selectScopeAsync().catch(e => {
                if (e) {
                    this.showError(e, true);
                }
            });
        });
    }

    /** 
     * Prompt user to select a scope 
     */
    async selectScopeAsync() {
        var dialog = new SelectScopeDialog();

        new LocalSettings().environment = await dialog.showAsync();
        
        window.location = window.location.href.split('#')[0];
    }

    /**
     * Load model based on the hashbang of the current location.
     */
    async loadModel() {
        this.model = window.location.host.startsWith("localhost:")
            ? new DebugModel()
            : new Model();

        //
        // Clear all views.
        //
        new DefaultView().showAsync();

        //
        // Determine resource to load.
        //
        const settings = new LocalSettings();
        let resource;
        if (window.location.hash && window.location.hash.startsWith('#!')) {
            resource = window.location.hash.substring(2);

            if (resource) {
                //
                // Extract environment name.
                //
                const regex = /^\/environments\/(.*?)(\/.*)?$/;
                const found = resource.match(regex);
                if (found && found.length >= 2) {
                    this.environment = found[1];

                    $('#jit-scope').text(this.environment);
                    $('title').html(`JIT Groups: ${this.environment}`);
                }
                else {
                    this.environment = null;
                }
            }
        }
        else if (settings.environment) {
            this.environment = settings.environment;
            resource = `/environments/${this.environment}`;

            $('#jit-scope').text(this.environment);
            $('title').html(`JIT Groups: ${this.environment}`);
        }

        if (!this.environment) {
            //
            // Configuration incomplete, show dialog.
            //
            await this.selectScopeAsync();
            return null;
        }

        //
        // Initialize model.
        //
        await this.model.initialize(this.environment, resource);

        $("#signed-in-user").text(this.model.context.subject.email);
        $("#application-version").text(this.model.context.application.version);

        return this.model;
    }

    /** 
     * Display an error bar at the top of the screen 
     */
    showError(message, isSevere) {
        console.assert(this._banner);

        this._banner.open();
        $('#jit-banner-text').text(message);

        if (isSevere) {
            $('#jit-banner-reloadbutton').on('click', () => {
                this._reloadPage();
            });
        }
        else {
            $('#jit-banner-reloadbutton').hide();
            setTimeout(() => this.clearError(), 10000 );
        }
    }

    clearError() {
        this._banner.close();
    }
}

$(document).ready(async () => {
    console.assert(mdc);

    $('body').prepend(`<header class="mdc-top-app-bar mdc-top-app-bar--dense">
          <div class="mdc-top-app-bar__row">
            <section class="mdc-top-app-bar__section mdc-top-app-bar__section--align-start">
                <span class="mdc-top-app-bar__title jit-title">
                    <img src='logo.png' alt='JIT Groups'/>
                    <a href="/">JIT Groups</a>
                </span>
                <button class="mdc-button mdc-button--outlined" id="jit-environmentselector">
                    <span class="mdc-button__ripple"></span>
                    <span class="mdc-button__label">
                        <span id="jit-scope">No environment selected</span>
                        <i class="material-icons mdc-button__icon" aria-hidden="true">expand_more</i>
                    </span>
                </button>
            </section>
            <section class="mdc-top-app-bar__section mdc-top-app-bar__section--align-end" role="toolbar">
                <button class="material-icons mdc-top-app-bar__action-item mdc-icon-button" aria-label="help">
                    <a href='https://googlecloudplatform.github.io/jit-access/?utm_source=jitgroups&utm_medium=help' class='jit-helpbutton' target='_blank'>help_center</a>
                </button>
            </section>
          </div>
        </header>`);
    $('main').prepend(`
        <div class="mdc-banner" role="banner">
            <div class="mdc-banner__content" role="alertdialog" aria-live="assertive">
                <div class="mdc-banner__graphic-text-wrapper">
                    <div class="mdc-banner__text" id="jit-banner-text">
                    </div>
                </div>
                <div class="mdc-banner__actions" id="jit-banner-reloadbutton">
                    <button type="button" class="mdc-button mdc-banner__primary-action">
                    <div class="mdc-button__ripple"></div>
                    <div class="mdc-button__label">Reload</div>
                    </button>
                </div>
            </div>
        </div>
        <div class='jit-view' id='jit-default-view'>
            Loading...
        </div>`);
    $('body').append(`
        <div class="mdc-dialog" id="jit-scopedialog">
            <div class="mdc-dialog__container">
            <div class="mdc-dialog__surface"
                role="alertdialog"
                aria-modal="true"
                aria-labelledby="scopedialog-title"
                aria-describedby="scopedialog-content">
                  
                <h2 class="mdc-dialog__title" id="scopedialog-title">Environment</h2>
                <div class="mdc-dialog__content" id="scopedialog-content">
                    <ul class="mdc-list mdc-list--two-line" id="jit-scopedialog-list">
                    </ul>
                </div>
                <div class="mdc-dialog__actions">
                    <button type="button" class="mdc-button mdc-dialog__button" data-mdc-dialog-action="close">
                        <div class="mdc-button__ripple"></div>
                        <span class="mdc-button__label">Cancel</span>
                    </button>
                </div>
            </div>
            </div>
            <div class="mdc-dialog__scrim"></div>
        </div>`)
    $('body').append(`
        <footer class="jit-footer">
            <div>Signed in as&nbsp;<span id="signed-in-user"></span>&nbsp;(<a href="?gcp-iap-mode=CLEAR_LOGIN_COOKIE">change</a>)</div>
            &nbsp;|&nbsp;
            <div>Powered by&nbsp;<a href="https://googlecloudplatform.github.io/jit-access/?utm_source=jitgroups&utm_medium=about">JIT Groups <span id="application-version"></span></a></div>
        </footer>`);
        
    mdc.autoInit();
    
    document.appbar = new AppBar();
});