"use strict"

/** Manage browser-local storage */
class LocalSettings {
    get lastProjectId() {
        if (typeof (Storage) !== "undefined") {
            return localStorage.getItem("projectId");
        }
        else {
            return null;
        }
    }

    set lastProjectId(projectId) {
        if (typeof (Storage) !== "undefined") {
            localStorage.setItem("projectId", projectId);
        }
    }
}

class Model {
    _getHeaders() {
        return { "X-JITACCESS": "1" };
    }

    _formatError(error) {
        let message = (error.responseJSON && error.responseJSON.message)
            ? error.responseJSON.message
            : "";
        return `${message} (HTTP ${error.status}: ${error.statusText})`;
    }

    get policy() {
        console.assert(this._policy);
        return this._policy;
    }

    async fetchPolicy() {
        try {
            await new Promise(r => setTimeout(r, 200));
            this._policy = await $.ajax({
                url: "/api/policy",
                dataType: "json",
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    async listProjects() {
        try {
            return await $.ajax({
                url: "/api/projects",
                dataType: "json",
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    async searchProjects(projectIdPrefix) {
        console.assert(projectIdPrefix);

        // Avoid kicking off multiple requests in parallel.
        if (!this._listProjectsPromise) {
            this._listProjectsPromise = this.listProjects();
        }

        let projectsResult = await this._listProjectsPromise;
        if (!projectsResult.projects) {
            return [];
        }

        return projectsResult.projects.filter(
            projectId => projectId.toLowerCase().includes(projectIdPrefix.trim().toLowerCase()));
    }

    /** List eligible roles */
    async listRoles(projectId) {
        console.assert(projectId);

        try {
            return await $.ajax({
                url: `/api/projects/${projectId}/roles`,
                dataType: "json",
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    /** List peers that can approve a request */
    async listPeers(projectId, role) {
        console.assert(projectId);
        console.assert(role);

        try {
            return await $.ajax({
                url: `/api/projects/${projectId}/peers?role=${encodeURIComponent(role)}`,
                dataType: "json",
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    /** Activate roles without peer approval */
    async selfApproveActivation(projectId, roles, justification, activationTimeout) {
        console.assert(projectId);
        console.assert(roles.length > 0);
        console.assert(justification)
        console.assert(activationTimeout)

        try {
            return await $.ajax({
                type: "POST",
                url: `/api/projects/${projectId}/roles/self-activate`,
                dataType: "json",
                contentType: "application/json; charset=utf-8",
                data: JSON.stringify({
                    roles: roles,
                    justification: justification,
                    activationTimeout: activationTimeout
                }),
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    /** Activate a role with peer approval */
    async requestActivation(projectId, role, peers, justification, activationTimeout) {
        console.assert(projectId);
        console.assert(role);
        console.assert(peers.length > 0);
        console.assert(justification)
        console.assert(activationTimeout)

        try {
            return await $.ajax({
                type: "POST",
                url: `/api/projects/${projectId}/roles/request`,
                dataType: "json",
                contentType: "application/json; charset=utf-8",
                data: JSON.stringify({
                    role: role,
                    justification: justification,
                    peers: peers,
                    activationTimeout: activationTimeout
                }),
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    /** Get details for an activation request */
    async getActivationRequest(activationToken) {
        console.assert(activationToken);

        try {
            return await $.ajax({
                url: `/api/activation-request?activation=${encodeURIComponent(activationToken)}`,
                dataType: "json",
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }

    /** Approve an activation request */
    async approveActivationRequest(activationToken) {
        console.assert(activationToken);

        try {
            return await $.ajax({
                type: "POST",
                url: `/api/activation-request?activation=${encodeURIComponent(activationToken)}`,
                headers: this._getHeaders()
            });
        }
        catch (error) {
            throw this._formatError(error);
        }
    }
}

class DebugModel extends Model {
    constructor() {
        super();
        $("body").append(`
            <div id="debug-pane">
                <div>
                Principal: <input type="text" id="debug-principal"/>
                </div>
                <hr/>
                <div>
                    listProjects:
                    <select id="debug-listProjects">
                        <option value="">(default)</option>
                        <option value="error">Simulate error</option>
                        <option value="0">Simulate 0 results</option>
                        <option value="10">Simulate 10 result</option>
                        <option value="100">Simulate 100 results</option>
                    </select>
                </div>
                <div>
                    listRoles:
                    <select id="debug-listRoles">
                        <option value="">(default)</option>
                        <option value="error">Simulate error</option>
                        <option value="0">Simulate 0 results</option>
                        <option value="1">Simulate 1 result</option>
                        <option value="10">Simulate 10 results</option>
                    </select>
                </div>
                <div>
                    listPeers:
                    <select id="debug-listPeers">
                        <option value="">(default)</option>
                        <option value="error">Simulate error</option>
                        <option value="0">Simulate 0 results</option>
                        <option value="1">Simulate 1 result</option>
                        <option value="10">Simulate 10 results</option>
                    </select>
                </div>
                <div>
                    selfApproveActivation:
                    <select id="debug-selfApproveActivation">
                        <option value="">(default)</option>
                        <option value="success">Simulate success</option>
                        <option value="error">Simulate error</option>
                    </select>
                </div>
                <div>
                    requestActivation:
                    <select id="debug-requestActivation">
                        <option value="">(default)</option>
                        <option value="success">Simulate success</option>
                        <option value="error">Simulate error</option>
                    </select>
                </div>
                <div>
                    getActivationRequest:
                    <select id="debug-getActivationRequest">
                        <option value="">(default)</option>
                        <option value="success">Simulate success</option>
                        <option value="error">Simulate error</option>
                    </select>
                </div>
                <div>
                    approveActivationRequest:
                    <select id="debug-approveActivationRequest">
                        <option value="">(default)</option>
                        <option value="success">Simulate success</option>
                        <option value="error">Simulate error</option>
                    </select>
                </div>
            </div>
        `);

        //
        // Persist settings.
        //
        [
            "debug-principal",
            "debug-listProjects",
            "debug-listRoles",
            "debug-listPeers",
            "debug-selfApproveActivation",
            "debug-requestActivation",
            "debug-getActivationRequest",
            "debug-approveActivationRequest"
        ].forEach(setting => {

            $("#" + setting).val(localStorage.getItem(setting))
            $("#" + setting).change(() => {
                localStorage.setItem(setting, $("#" + setting).val());
            });
        });
    }

    _getHeaders() {
        const headers = super._getHeaders();
        const principal = $("#debug-principal").val();
        if (principal) {
            headers["X-Debug-Principal"] = principal;
        }
        return headers;
    }

    async _simulateError() {
        await new Promise(r => setTimeout(r, 1000));
        return Promise.reject("Simulated error");
    }

    async _simulateActivationResponse(projectId, justification, roles, status, forSelf, activationTimeout) {
        await new Promise(r => setTimeout(r, 1000));
        return Promise.resolve({
            isBeneficiary: forSelf,
            isReviewer: (!forSelf),
            justification: justification,
            beneficiary: { email: "user" },
            reviewers: forSelf ? [] : [{ email: "reviewer"}],
            items: roles.map(r => ({
                activationId: "sim-1",
                projectId: projectId,
                roleBinding: {
                    fullResourceName: "//simulated",
                    role: r
                },
                status: status,
                startTime: Math.floor(Date.now() / 1000),
                endTime: Math.floor(Date.now() / 1000) + activationTimeout * 60
            }))
        });
    }

    get policy() {
        return {
            justificationHint: "simulated hint",
            signedInUser: {
                email: "user@example.com"
            },
            defaultActivationTimeout: 60,
            maxActivationTimeout: 120
        };
    }

    async fetchPolicy() { }

    async listRoles(projectId) {
        var setting = $("#debug-listRoles").val();
        if (!setting) {
            return super.listRoles(projectId);
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            await new Promise(r => setTimeout(r, 2000));
            const statuses = ["ACTIVATED", "ELIGIBLE_FOR_JIT", "ELIGIBLE_FOR_MPA"]
            return Promise.resolve({
                warnings: ["This is a simulated result"],
                roles: Array.from({ length: setting }, (e, i) => ({
                    roleBinding: {
                        id: "//project-1:roles/simulated-role-" + i,
                        role: "roles/simulated-role-" + i
                    },
                    status: statuses[i % statuses.length]
                }))
            });
        }
    }

    async listProjects() {
        var setting = $("#debug-listProjects").val();
        if (!setting) {
            return super.listProjects();
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            await new Promise(r => setTimeout(r, 2000));
            return Promise.resolve({
                projects: Array.from({ length: setting }, (e, i) => "project-" + i)
            });
        }
    }

    async listPeers(projectId, role) {
        var setting = $("#debug-listPeers").val();
        if (!setting) {
            return super.listPeers(projectId, role);
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            await new Promise(r => setTimeout(r, 1000));
            return Promise.resolve({
                peers: Array.from({ length: setting }, (e, i) => ({
                    email: `user-${i}@example.com`
                }))
            });
        }
    }

    async selfApproveActivation(projectId, roles, justification, activationTimeout) {
        var setting = $("#debug-selfApproveActivation").val();
        if (!setting) {
            return super.selfApproveActivation(projectId, roles, justification, activationTimeout);
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            return await this._simulateActivationResponse(
                projectId,
                justification,
                roles,
                "ACTIVATED",
                true,
                activationTimeout);
        }
    }

    async requestActivation(projectId, role, peers, justification, activationTimeout) {
        var setting = $("#debug-requestActivation").val();
        if (!setting) {
            return super.requestActivation(projectId, role, peers, justification, activationTimeout);
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            return await this._simulateActivationResponse(
                projectId,
                justification,
                [role],
                "ACTIVATION_PENDING",
                true,
                activationTimeout);
        }
    }

    async getActivationRequest(activationToken) {
        var setting = $("#debug-getActivationRequest").val();
        if (!setting) {
            return super.getActivationRequest(activationToken);
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            return await this._simulateActivationResponse(
                "project-1",
                "a justification",
                ["roles/role-1"],
                "ACTIVATION_PENDING",
                false,
                60);
        }
    }

    async approveActivationRequest(activationToken) {
        var setting = $("#debug-approveActivationRequest").val();
        if (!setting) {
            return super.approveActivationRequest(activationToken);
        }
        else if (setting === "error") {
            await this._simulateError();
        }
        else {
            return await this._simulateActivationResponse(
                "project-1",
                "a justification",
                ["roles/role-1"],
                "ACTIVATED",
                false,
                60);
        }
    }
}