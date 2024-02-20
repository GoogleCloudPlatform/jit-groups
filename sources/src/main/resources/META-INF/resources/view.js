"use strict"

//-----------------------------------------------------------------------------
// Data Table extensions.
//-----------------------------------------------------------------------------

mdc.dataTable.MDCDataTable.prototype.clearRows = function() {
    this.content.innerHTML = '';
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
        div.text(value.text);
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

        td.append(div);
        tr.append(td);
    });

    //
    // Update internal bindings.
    //
    this.layout();
};

//-----------------------------------------------------------------------------
// Base classes.
//-----------------------------------------------------------------------------

class ObservableProperty {
    constructor(value) {
        this._value = value;
        this._onChangeListeners = [];
    }

    get value() {
        return this._value;
    }

    set value(v) {
        this._value = v;
        this._onChangeListeners.forEach(cb => cb(v));
    }

    onChange(callback) {
        this._onChangeListeners.push(callback)
    }
}

class Dialog {
    constructor(selector) {
        this._element = new mdc.dialog.MDCDialog(document.querySelector(selector));
    }

    /** return the dialog result */
    get result() {
        return null;
    }

    /** show dialog and await result */
    showAsync() {
        return new Promise((resolve, reject) => {
            this._element.listen('MDCDialog:closed', e => {
                if (e.detail.action == "accept") {
                    resolve(this.result);
                }
                else {
                    reject();
                }
            });

            this._element.open();
        });
    }
}

class SelectScopeDialog extends Dialog {
    constructor() {
        super('#jit-scopedialog');
        
        const textField = new mdc.textField.MDCTextField(document.querySelector('.mdc-text-field'));

        $('#jit-scopedialog-input').on('change', e => {
            $('#jit-scopedialog-ok').prop('disabled', $('#jit-scopedialog-input').val() == '');
        });

        //
        // Configure autocompleter.
        //
        $("#jit-scopedialog-input").autocomplete({
            source: async (request, response) => {
                try {
                    const projects = await document.model.searchProjects(request.term);
                    response($.map(projects, (item) => {
                        return {
                            label: item,
                            value: item
                        };
                    }));
                }
                catch (e) {
                    this.showError(`Loading projects failed: ${e}`, false); //TODO: fix
                }
            },
            minLength: 2,
            position: { of: $("#jit-scopedialog-input") },
            open: function() {
                $("ul.ui-menu").width($(this).innerWidth());
            },
            select: (e, ui) => {
                $('#jit-scopedialog-ok').prop('disabled', ui.item.value == '');
            }
        });
    }

    get result() {
        return $('#jit-scopedialog-input').val();
    }
}

class AppBar {
    constructor() {
        this._banner = new mdc.banner.MDCBanner(document.querySelector('.mdc-banner'));
        
        this.scope = new ObservableProperty();
        this.scope.onChange(scope => {
            $('#jit-scope').text(scope);

            //TODO: hide if scope not set?
        })

        //
        // Setup bindings.
        //

        $('#jit-projectselector').on('click', () => {
            this.selectScopeAsync();
        });
    }

    async selectScopeAsync() {
        var dialog = new SelectScopeDialog();
        this.scope.value = await dialog.showAsync();
    }

    /** Display an error bar at the top of the screen */
    showError(message, showReloadButton) {
        console.assert(this._banner);

        this._banner.open();
        $('#jit-banner-text').text(message);

        if (showReloadButton) {
            $('#jit-banner-reloadbutton').on('click', () => {
                const timestamp = Date.now();
                window.location.href = `${window.location.pathname}?_=${timestamp}`;
            });
        }
        else {
            $('#jit-banner-reloadbutton').hide();
        }
    }
}

$(document).ready(async () => {
    console.assert(mdc);

    $('body').prepend(`<header class="mdc-top-app-bar mdc-top-app-bar--dense">
          <div class="mdc-top-app-bar__row">
            <section class="mdc-top-app-bar__section mdc-top-app-bar__section--align-start">
                <span class="mdc-top-app-bar__title jit-title">
                    <img src='logo.png' alt='JIT Access'/>
                    Just-in-Time Access
                </span>
                <button class="mdc-button mdc-button--outlined" id="jit-projectselector">
                    <span class="mdc-button__ripple"></span>
                    <span class="mdc-button__label">
                        <span id="jit-scope">No project selected</span>
                        <i class="material-icons mdc-button__icon" aria-hidden="true">expand_more</i>
                    </span>
                </button>
            </section>
            <section class="mdc-top-app-bar__section mdc-top-app-bar__section--align-end" role="toolbar">
                <button class="material-icons mdc-top-app-bar__action-item mdc-icon-button" aria-label="help">
                    <a href='https://googlecloudplatform.github.io/jit-access/' class='jit-helpbutton' target='_blank'>help_center</a>
                </button>
            </section>
          </div>
        </header>`);
    $('main').append(`
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
        </div>`);
    $('body').append(`
        <div class="mdc-dialog" id="jit-scopedialog">
            <div class="mdc-dialog__container">
            <div class="mdc-dialog__surface"
                role="alertdialog"
                aria-modal="true"
                aria-labelledby="scopedialog-title"
                aria-describedby="scopedialog-content">
                  
                <h2 class="mdc-dialog__title" id="scopedialog-title">Select project</h2>
                <div class="mdc-dialog__content" id="scopedialog-content">
                <label class="mdc-text-field mdc-text-field--outlined">
                    <span class="mdc-notched-outline">
                        <span class="mdc-notched-outline__leading"></span>
                        <span class="mdc-notched-outline__notch">
                            <span class="mdc-floating-label">Project ID</span>
                        </span>
                        <span class="mdc-notched-outline__trailing"></span>
                    </span>
                    <input type="text" class="mdc-text-field__input" id="jit-scopedialog-input" autofocus>
                </label>
                </div>
                <div class="mdc-dialog__actions">
                <button type="button" class="mdc-button mdc-dialog__button" data-mdc-dialog-action="close">
                    <div class="mdc-button__ripple"></div>
                    <span class="mdc-button__label">Cancel</span>
                </button>
                <button type="button" class="mdc-button mdc-dialog__button  mdc-button--raised" data-mdc-dialog-action="accept" id="jit-scopedialog-ok" disabled>
                    <div class="mdc-button__ripple"></div>
                    <span class="mdc-button__label">OK</span>
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
            <div>Powered by&nbsp;<a href="https://googlecloudplatform.github.io/jit-access/">JIT Access <span id="application-version"></span></a></div>
        </footer>`);
        
    mdc.autoInit();
    
    const queryParameters = new URLSearchParams(location.search);
    document.model = queryParameters.get("debug") ? new DebugModel() : new Model();
    document.appbar = new AppBar();

    try {
        //
        // Download policy to check if the communication with the model
        // works properly. 
        //
        await document.model.fetchPolicy();
        
        const localSettings = new LocalSettings();
        document.appbar.scope.value = queryParameters.get("projectId") ?? localSettings.lastProjectId;
        document.appbar.scope.onChange(newScope => {
            localSettings.lastProjectId = newScope;
        });

        $("#signed-in-user").text(document.model.policy.signedInUser.email);
        $("#application-version").text(document.model.policy.applicationVersion);

    }
    catch (error) {
        document.appbar.showError(error, true);
    }
});