/**
 * Certificate Deployment Manager (CDM) - Frontend Application
 * Enterprise Single Page Application (SPA) Controller
 */

// Application State
const state = {
  auth: {
    username: localStorage.getItem('cdm_username') || 'cdm_admin',
    password: localStorage.getItem('cdm_password') || 'admin_dev_password_change_me',
    role: 'ROLE_ADMIN'
  },
  currentTab: 'dashboard',
  refreshInterval: null,
  refreshRate: 10000,
  data: {
    systemStatus: null,
    actuatorHealth: null,
    certificates: [],
    servers: [],
    replacements: [],
    deployments: [],
    midServers: [],
    auditEvents: [],
    metrics: {}
  }
};

// API Helper
async function apiRequest(endpoint, options = {}) {
  const headers = {
    'Accept': 'application/json',
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  // Add Basic Auth if not explicitly disabled
  if (!options.noAuth && state.auth.username && state.auth.password) {
    const creds = btoa(`${state.auth.username}:${state.auth.password}`);
    headers['Authorization'] = `Basic ${creds}`;
  }

  try {
    const response = await fetch(endpoint, {
      ...options,
      headers
    });

    if (response.status === 401) {
      showToast('Authentication failed: Please check credentials in Settings', 'danger');
      openAuthModal();
      return null;
    }

    if (response.status === 403) {
      showToast('Access Forbidden: Current role has insufficient privileges', 'danger');
      return null;
    }

    const json = await response.json();
    return json;
  } catch (error) {
    console.error(`API request failed for ${endpoint}:`, error);
    return null;
  }
}

// Toast Notifications
function showToast(message, type = 'info', duration = 3500) {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast badge-${type}`;
  
  let icon = 'ℹ️';
  if (type === 'success') icon = '✅';
  if (type === 'danger') icon = '❌';
  if (type === 'warning') icon = '⚠️';

  toast.innerHTML = `
    <span>${icon}</span>
    <span>${message}</span>
  `;

  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(10px)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, duration);
}

// Tab Navigation
function initNavigation() {
  const navItems = document.querySelectorAll('.nav-item');
  navItems.forEach(item => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      const tab = item.getAttribute('data-tab');
      if (tab) switchTab(tab);
    });
  });
}

function switchTab(tabName) {
  state.currentTab = tabName;

  // Update navigation items
  document.querySelectorAll('.nav-item').forEach(item => {
    if (item.getAttribute('data-tab') === tabName) {
      item.classList.add('active');
    } else {
      item.classList.remove('active');
    }
  });

  // Update tab panes
  document.querySelectorAll('.tab-pane').forEach(pane => {
    if (pane.id === `tab-${tabName}`) {
      pane.classList.add('active');
    } else {
      pane.classList.remove('active');
    }
  });

  // Update Page Title
  const titleMap = {
    dashboard: 'Operational Dashboard',
    certificates: 'SSL/TLS Certificate Inventory',
    servers: 'Target Server Infrastructure',
    replacements: 'Matching & Correlation Studio',
    deployments: 'Deployment Orchestrator',
    midservers: 'ServiceNow MID Server Fleet',
    audit: 'Immutable Audit Trail & Compliance',
    simulator: 'Live E2E Lifecycle Simulator'
  };
  document.getElementById('page-title').innerText = titleMap[tabName] || 'CDM Management Console';

  // Trigger Data Load for Tab
  refreshActiveTab();
}

function refreshActiveTab() {
  switch (state.currentTab) {
    case 'dashboard':
      loadDashboard();
      break;
    case 'certificates':
      loadCertificates();
      break;
    case 'servers':
      loadServers();
      break;
    case 'replacements':
      loadReplacements();
      break;
    case 'deployments':
      loadDeployments();
      break;
    case 'midservers':
      loadMidServers();
      break;
    case 'audit':
      loadAuditEvents();
      break;
    case 'simulator':
      // Simulator maintains its own state
      break;
  }
}

// ==========================================================================
// View Loaders
// ==========================================================================

// 1. Dashboard View
async function loadDashboard() {
  const [statusRes, healthRes, certsRes, serversRes, depsRes, midRes] = await Promise.all([
    apiRequest('/api/v1/system/status', { noAuth: true }),
    apiRequest('/actuator/health', { noAuth: true }),
    apiRequest('/api/v1/certificates?page=0&size=1'),
    apiRequest('/api/v1/servers?page=0&size=1'),
    apiRequest('/api/v1/deployments?page=0&size=1'),
    apiRequest('/api/v1/mid-servers?page=0&size=1')
  ]);

  if (statusRes && statusRes.success) {
    state.data.systemStatus = statusRes.data;
    const dbStatus = statusRes.data.components?.database || 'UP';
    document.getElementById('db-status-pill').innerHTML = `<span class="pulse-dot"></span> PostgreSQL: ${dbStatus.split(' ')[0]}`;
  }

  // Update Metrics KPIs
  const totalCerts = certsRes?.data?.totalElements ?? 0;
  const totalServers = serversRes?.data?.totalElements ?? 0;
  const totalDeployments = depsRes?.data?.totalElements ?? 0;
  const totalMidServers = midRes?.data?.totalElements ?? 0;

  document.getElementById('kpi-total-certs').innerText = totalCerts;
  document.getElementById('kpi-total-servers').innerText = totalServers;
  document.getElementById('kpi-total-deployments').innerText = totalDeployments;
  document.getElementById('kpi-total-mid').innerText = totalMidServers;
}

// 2. Certificates View
async function loadCertificates() {
  const statusFilter = document.getElementById('cert-status-filter')?.value || '';
  const search = document.getElementById('cert-search-input')?.value || '';

  let url = `/api/v1/certificates?page=0&size=50&sortBy=createdAt&direction=DESC`;
  if (statusFilter) url += `&status=${statusFilter}`;
  if (search) url += `&commonName=${encodeURIComponent(search)}`;

  const res = await apiRequest(url);
  const tbody = document.getElementById('certs-table-body');
  if (!tbody) return;

  if (!res || !res.success || !res.data?.content?.length) {
    tbody.innerHTML = `<tr><td colspan="7" class="text-center" style="padding: 30px; color: var(--text-muted);">No certificates discovered in repository. Use Simulator to populate.</td></tr>`;
    return;
  }

  state.data.certificates = res.data.content;

  tbody.innerHTML = res.data.content.map(cert => `
    <tr>
      <td>
        <div style="font-weight: 600; color: var(--text-primary);">${cert.commonName}</div>
        <div style="font-size: 11px; color: var(--text-muted);">${cert.serialNumber || 'N/A'}</div>
      </td>
      <td><span class="badge ${cert.source === 'SECTIGO' ? 'badge-purple' : 'badge-info'}">${cert.source}</span></td>
      <td><span class="badge ${cert.status === 'ACTIVE' ? 'badge-success' : 'badge-neutral'}">${cert.status}</span></td>
      <td><span class="hash-cell">${(cert.thumbprint || '').substring(0, 16)}...</span></td>
      <td>${cert.issuer || 'DigiCert Global Root CA'}</td>
      <td style="font-size: 11.5px; color: var(--text-secondary);">${cert.validTo ? new Date(cert.validTo).toLocaleDateString() : 'N/A'}</td>
      <td>
        <button class="btn btn-secondary btn-sm" onclick="inspectItem('certificate', '${cert.id}')">Inspect</button>
      </td>
    </tr>
  `).join('');
}

// 3. Target Servers View
async function loadServers() {
  const res = await apiRequest('/api/v1/servers?page=0&size=50&sortBy=hostname&direction=ASC');
  const tbody = document.getElementById('servers-table-body');
  if (!tbody) return;

  if (!res || !res.success || !res.data?.content?.length) {
    tbody.innerHTML = `<tr><td colspan="6" class="text-center" style="padding: 30px; color: var(--text-muted);">No target servers configured.</td></tr>`;
    return;
  }

  state.data.servers = res.data.content;

  tbody.innerHTML = res.data.content.map(srv => `
    <tr>
      <td style="font-weight: 600; color: var(--text-primary);">${srv.hostname}</td>
      <td><span class="badge badge-info">${srv.technology}</span></td>
      <td><span class="badge ${srv.environment === 'PRODUCTION' ? 'badge-danger' : 'badge-neutral'}">${srv.environment}</span></td>
      <td><span class="badge ${srv.status === 'ACTIVE' ? 'badge-success' : 'badge-neutral'}">${srv.status}</span></td>
      <td class="font-mono">${srv.ipAddress || '10.0.4.12'} : ${srv.port || 443}</td>
      <td>
        <button class="btn btn-secondary btn-sm" onclick="inspectItem('server', '${srv.id}')">Details</button>
      </td>
    </tr>
  `).join('');
}

// 4. Replacements View
async function loadReplacements() {
  const res = await apiRequest('/api/v1/replacements?page=0&size=50&sortBy=createdAt&direction=DESC');
  const tbody = document.getElementById('replacements-table-body');
  if (!tbody) return;

  if (!res || !res.success || !res.data?.content?.length) {
    tbody.innerHTML = `<tr><td colspan="6" class="text-center" style="padding: 30px; color: var(--text-muted);">No replacement correlations registered.</td></tr>`;
    return;
  }

  state.data.replacements = res.data.content;

  tbody.innerHTML = res.data.content.map(rep => {
    const scorePct = Math.round((rep.matchingScore || 0) * 100);
    return `
      <tr>
        <td class="font-mono" style="font-size: 11.5px;">${rep.id.substring(0, 8)}...</td>
        <td style="color: var(--warning);">${rep.oldCommonName || 'api.example.com (Old)'}</td>
        <td style="color: var(--success);">${rep.newCommonName || 'api.example.com (New)'}</td>
        <td>
          <div style="display: flex; align-items: center; gap: 8px;">
            <div style="flex: 1; height: 6px; background: rgba(255,255,255,0.1); border-radius: 3px; max-width: 80px;">
              <div style="width: ${scorePct}%; height: 100%; background: var(--accent-gradient); border-radius: 3px;"></div>
            </div>
            <span style="font-weight: 700; font-size: 11.5px;">${scorePct}%</span>
          </div>
        </td>
        <td><span class="badge badge-success">${rep.matchStatus}</span></td>
        <td>
          <button class="btn btn-secondary btn-sm" onclick="inspectItem('replacement', '${rep.id}')">Inspect</button>
        </td>
      </tr>
    `;
  }).join('');
}

// 5. Deployments View
async function loadDeployments() {
  const res = await apiRequest('/api/v1/deployments?page=0&size=50&sortBy=createdAt&direction=DESC');
  const tbody = document.getElementById('deployments-table-body');
  if (!tbody) return;

  if (!res || !res.success || !res.data?.content?.length) {
    tbody.innerHTML = `<tr><td colspan="7" class="text-center" style="padding: 30px; color: var(--text-muted);">No deployment jobs found.</td></tr>`;
    return;
  }

  state.data.deployments = res.data.content;

  tbody.innerHTML = res.data.content.map(dep => {
    let badgeClass = 'badge-info';
    if (dep.status === 'COMPLETED' || dep.status === 'CONFIRMED' || dep.status === 'VERIFIED') badgeClass = 'badge-success';
    if (dep.status === 'FAILED' || dep.status === 'CONFIRMATION_FAIL') badgeClass = 'badge-danger';
    if (dep.status === 'RETRY_PENDING' || dep.status === 'PENDING_CONFIRMATION') badgeClass = 'badge-warning';

    return `
      <tr>
        <td class="font-mono" style="font-weight: 600; color: var(--text-primary);">${dep.jobReference || dep.id.substring(0, 8)}</td>
        <td style="font-weight: 600;">${dep.targetHost || 'WEB01'}</td>
        <td><span class="badge badge-neutral">${dep.deploymentType || 'IIS_BINDING'}</span></td>
        <td><span class="badge ${badgeClass}">${dep.status}</span></td>
        <td class="font-mono" style="font-size: 11px; color: var(--text-muted);">${dep.createdAt ? new Date(dep.createdAt).toLocaleTimeString() : 'Just now'}</td>
        <td>
          <div style="display: flex; gap: 6px;">
            <button class="btn btn-primary btn-sm" onclick="triggerRetry('${dep.id}')" title="Trigger Manual Retry">🔁 Retry</button>
            <button class="btn btn-success btn-sm" onclick="triggerVerify('${dep.id}')" title="Trigger Live TLS Verification">🔍 Verify</button>
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

// 6. MID Servers View
async function loadMidServers() {
  const res = await apiRequest('/api/v1/mid-servers?page=0&size=20&sortBy=name&direction=ASC');
  const tbody = document.getElementById('mid-table-body');
  if (!tbody) return;

  if (!res || !res.success || !res.data?.content?.length) {
    tbody.innerHTML = `<tr><td colspan="5" class="text-center" style="padding: 30px; color: var(--text-muted);">No MID servers registered.</td></tr>`;
    return;
  }

  state.data.midServers = res.data.content;

  tbody.innerHTML = res.data.content.map(mid => `
    <tr>
      <td style="font-weight: 600; color: var(--text-primary);">${mid.name}</td>
      <td><span class="badge ${mid.status === 'UP' ? 'badge-success' : 'badge-danger'}">${mid.status}</span></td>
      <td class="font-mono">${mid.ipAddress || '10.0.1.50'}</td>
      <td style="color: var(--text-muted); font-size: 12px;">${mid.capabilities?.join(', ') || 'Windows, Linux, Java'}</td>
      <td>
        <button class="btn btn-secondary btn-sm" onclick="probeMidHealth('${mid.id}')">🩺 Health Probe</button>
      </td>
    </tr>
  `).join('');
}

// 7. Audit Events View
async function loadAuditEvents() {
  const res = await apiRequest('/api/v1/audit-events?page=0&size=50&sortBy=timestamp&direction=DESC');
  const tbody = document.getElementById('audit-table-body');
  if (!tbody) return;

  if (!res || !res.success || !res.data?.content?.length) {
    tbody.innerHTML = `<tr><td colspan="6" class="text-center" style="padding: 30px; color: var(--text-muted);">No audit records found.</td></tr>`;
    return;
  }

  state.data.auditEvents = res.data.content;

  tbody.innerHTML = res.data.content.map(log => `
    <tr>
      <td class="font-mono" style="font-size: 11px; color: var(--text-muted);">${log.timestamp ? new Date(log.timestamp).toLocaleString() : 'N/A'}</td>
      <td style="font-weight: 600; color: var(--text-primary);">${log.action}</td>
      <td><span class="badge badge-neutral">${log.actor || 'CDM_SYSTEM'}</span></td>
      <td><span class="badge ${log.outcome === 'SUCCESS' ? 'badge-success' : 'badge-danger'}">${log.outcome}</span></td>
      <td class="font-mono" style="font-size: 11px; color: var(--text-code);">${log.correlationId || 'N/A'}</td>
      <td>
        <button class="btn btn-secondary btn-sm" onclick="viewAuditJson('${log.id}')">JSON</button>
      </td>
    </tr>
  `).join('');
}

// Actions
async function triggerRetry(jobId) {
  showToast(`Initiating manual retry for job ${jobId.substring(0, 8)}...`, 'info');
  const res = await apiRequest(`/api/v1/deployments/${jobId}/retry`, {
    method: 'POST',
    headers: { 'X-Correlation-ID': `CDM-MANUAL-RETRY-${Date.now()}` }
  });

  if (res && res.success) {
    showToast(`Retry dispatched successfully! Status: ${res.data.status}`, 'success');
    loadDeployments();
  } else {
    showToast('Failed to trigger retry: ' + (res?.message || 'Server error'), 'danger');
  }
}

async function triggerVerify(jobId) {
  showToast(`Executing live TLS handshake verification for job ${jobId.substring(0, 8)}...`, 'info');
  const res = await apiRequest(`/api/v1/deployments/${jobId}/verify`, {
    method: 'POST',
    headers: { 'X-Correlation-ID': `CDM-MANUAL-VERIFY-${Date.now()}` }
  });

  if (res && res.success) {
    showToast(`Verification complete! Status: ${res.data.status}`, 'success');
    loadDeployments();
  } else {
    showToast('Verification failed: ' + (res?.message || 'Endpoint unreachable'), 'danger');
  }
}

async function probeMidHealth(midId) {
  showToast('Probing MID Server agent node health...', 'info');
  const res = await apiRequest(`/api/v1/mid-servers/${midId}/health`);
  if (res && res.success) {
    showToast(`MID Server is ${res.data.status}! Roundtrip: ${res.data.latencyMs || 12}ms`, 'success');
  } else {
    showToast('MID Server health probe failed', 'danger');
  }
}

// Modal & Inspector
function inspectItem(type, id) {
  let item = null;
  if (type === 'certificate') item = state.data.certificates.find(c => c.id === id);
  if (type === 'server') item = state.data.servers.find(s => s.id === id);
  if (type === 'replacement') item = state.data.replacements.find(r => r.id === id);

  if (!item) return;

  document.getElementById('modal-title').innerText = `${type.toUpperCase()} Details: ${id.substring(0, 8)}...`;
  document.getElementById('modal-json-content').innerText = JSON.stringify(item, null, 2);
  openModal();
}

function viewAuditJson(logId) {
  const log = state.data.auditEvents.find(l => l.id === logId);
  if (!log) return;

  document.getElementById('modal-title').innerText = `Audit Event: ${log.action}`;
  document.getElementById('modal-json-content').innerText = JSON.stringify(log, null, 2);
  openModal();
}

function openModal() {
  document.getElementById('inspector-modal').classList.add('active');
}

function closeModal() {
  document.getElementById('inspector-modal').classList.remove('active');
  document.getElementById('auth-modal').classList.remove('active');
}

function openAuthModal() {
  document.getElementById('auth-username-input').value = state.auth.username;
  document.getElementById('auth-password-input').value = state.auth.password;
  document.getElementById('auth-modal').classList.add('active');
}

function saveAuthCredentials() {
  const user = document.getElementById('auth-username-input').value.trim();
  const pass = document.getElementById('auth-password-input').value.trim();

  state.auth.username = user;
  state.auth.password = pass;
  localStorage.setItem('cdm_username', user);
  localStorage.setItem('cdm_password', pass);

  document.getElementById('current-username').innerText = user;
  closeModal();
  showToast('Credentials updated successfully', 'success');
  refreshActiveTab();
}

// ==========================================================================
// Interactive E2E Simulation Runner
// ==========================================================================
let simRunning = false;

async function runClientSimulation() {
  if (simRunning) return;
  simRunning = true;

  const consoleBox = document.getElementById('sim-console-output');
  const btn = document.getElementById('sim-run-btn');
  btn.disabled = true;
  btn.innerText = '⏳ Simulation In Progress...';
  consoleBox.innerHTML = '';

  function log(tag, msg, color = 'var(--text-primary)') {
    const time = new Date().toLocaleTimeString();
    const line = document.createElement('div');
    line.className = 'console-log-line';
    line.innerHTML = `
      <span class="console-time">[${time}]</span>
      <span class="console-tag badge-info">${tag}</span>
      <span class="console-msg" style="color: ${color};">${msg}</span>
    `;
    consoleBox.appendChild(line);
    consoleBox.scrollTop = consoleBox.scrollHeight;
  }

  function setStepActive(stepNum) {
    document.querySelectorAll('.step-item').forEach((item, idx) => {
      if (idx + 1 < stepNum) {
        item.className = 'step-item completed';
      } else if (idx + 1 === stepNum) {
        item.className = 'step-item active';
      } else {
        item.className = 'step-item';
      }
    });
  }

  const sleep = ms => new Promise(r => setTimeout(r, ms));

  log('SYS', 'Starting Full End-to-End CDM Closed-Loop Orchestration...', 'var(--info)');
  setStepActive(1);
  await sleep(700);

  log('SERVICENOW', 'Discovered expiring SSL/TLS certificate on WEB01 (CN=api.example.com, Issuer=DigiCert, Thumbprint=A1B2C3D4...)');
  setStepActive(2);
  await sleep(800);

  log('SECTIGO', 'Discovered renewed SSL/TLS certificate (CN=api.example.com, Issuer=Sectigo RSA, Thumbprint=F0E1D2C3...)');
  setStepActive(3);
  await sleep(700);

  log('MATCHER', 'Algorithmic Certificate Matcher evaluated confidence score: 0.96 (Status: AUTO_MATCHED)', 'var(--success)');
  setStepActive(4);
  await sleep(800);

  log('PLANNER', 'Deployment Planner generated DeploymentJob reference JOB-20260929-2E62F72C (Priority: CRITICAL, Tech: IIS)');
  setStepActive(5);
  await sleep(600);

  log('CYBERARK', 'Ephemeral JIT target credentials acquired for WEB01. In-memory password zeroed immediately.', 'var(--purple)');
  setStepActive(6);
  await sleep(800);

  log('MID_SERVER', 'Dispatched execution payload to ServiceNow MID Server node (Task ID: MID-TASK-WIN-IIS-999)');
  setStepActive(7);
  await sleep(900);

  log('ADAPTER', 'Simulated IIS Web Binding applied on port 443 with new certificate. Exit code: 0', 'var(--success)');
  setStepActive(8);
  await sleep(700);

  log('VERIFICATION', 'Direct live TLS SNI handshake performed against WEB01:443 -> Server returned expected Thumbprint F0E1D2C3... Handshake OK!', 'var(--success)');
  setStepActive(9);
  await sleep(900);

  log('CONFIRMATION', 'Day 2 ServiceNow CMDB Discovery reconciled: Certificate observed on WEB01. Deployment confirmed!', 'var(--success)');
  setStepActive(10);
  await sleep(600);

  log('AUDIT', 'Tamper-evident audit event recorded with correlationId E2E-CORR-20260929. Closed-loop complete!', 'var(--success)');
  
  btn.disabled = false;
  btn.innerText = '▶️ Run Simulated Renewal Cycle';
  simRunning = false;
  showToast('End-to-End Simulation Completed with 100% Success!', 'success');
}

// Initialization on DOM Ready
document.addEventListener('DOMContentLoaded', () => {
  initNavigation();
  loadDashboard();

  // Auto-refresh timer
  state.refreshInterval = setInterval(() => {
    refreshActiveTab();
  }, state.refreshRate);
});
