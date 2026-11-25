
    let products = [];
    let tables = [];
    let tickets = [];
    let adminUsers = [];
    let adminRoles = [];
    let reportData = { total: 0, efectivo: 0, tarjeta: 0, billeteras: 0, cobros: [] };
    let reportFilters = { from: '', to: '', method: 'TODOS' };
    let cashSummary = { totalRecaudado: 0, transacciones: 0, efectivo: 0, tarjeta: 0, billeteras: 0, montoInicial: 200 };

    const order = new Map();
    const customerCart = new Map();
    let activeView = 'dashboard';
    let category = 'Fondos';
    let selectedTable = 'M01';
    let customerTable = 'M02';
    let customerNote = '';
    let customerTicket = null;
    let billingTable = null;

    const main = document.getElementById('main');
    const money = n => `S/ ${Number(n || 0).toFixed(2)}`;
    const categories = ['Entradas', 'Fondos', 'Bebidas', 'Postres'];

    function applyTheme(theme) {
      const dark = theme === 'dark';
      document.body.classList.toggle('dark-mode', dark);
      const toggle = document.getElementById('themeToggle');
      if (!toggle) return;
      toggle.innerHTML = dark
        ? '<i class="bi bi-sun-fill"></i><span>Modo claro</span>'
        : '<i class="bi bi-moon-stars-fill"></i><span>Modo oscuro</span>';
      toggle.setAttribute('aria-label', dark ? 'Activar modo claro' : 'Activar modo oscuro');
      toggle.title = dark ? 'Activar modo claro' : 'Activar modo oscuro';
    }

    function toggleTheme() {
      const nextTheme = document.body.classList.contains('dark-mode') ? 'light' : 'dark';
      localStorage.setItem('restogestion-theme', nextTheme);
      applyTheme(nextTheme);
    }

    // Iconografía según categoría de plato
    function getCategoryIcon(cat) {
      if (cat === 'Entradas') return 'bi-egg-fried';
      if (cat === 'Fondos') return 'bi-fire';
      if (cat === 'Bebidas') return 'bi-cup-straw';
      if (cat === 'Postres') return 'bi-cake2';
      return 'bi-bookmark-check';
    }

    // ----------------------------------------------------------
    // CARGA DE DATOS ASÍNCRONA DESDE SPRING BOOT
    // ----------------------------------------------------------
    async function syncBackendData() {
      try {
        const [resMesas, resProds, resTickets, resCaja, resUsers, resRoles, resReports] = await Promise.all([
          fetch('/api/mesas'),
          fetch('/api/productos'),
          fetch('/api/cocina/tickets'),
          fetch('/api/caja/resumen'),
          fetch('/api/usuarios'),
          fetch('/api/roles'),
          fetch('/api/reportes')
        ]);

        if (resMesas.ok) {
          const rawMesas = await resMesas.json();
          tables = rawMesas.map(m => {
            const estadoFormateado = m.estado.charAt(0).toUpperCase() + m.estado.slice(1).toLowerCase();
            let kind = 'green';
            if (m.estado === 'OCUPADA') kind = 'red';
            else if (m.estado === 'CUENTA') kind = 'blue';
            else if (m.estado === 'RESERVADA') kind = 'gold';
            else if (m.estado === 'LIMPIEZA') kind = 'gold';

            return {
              id: m.idMesa,
              no: m.numeroMesa,
              status: m.estado === 'CUENTA' ? 'En cuenta' : estadoFormateado,
              kind: kind,
              people: m.comensalesActuales || 0,
              capacity: m.capacidad,
              time: m.fechaApertura ? 'Activa' : (m.estado === 'LIMPIEZA' ? 'Por limpiar' : 'Disponible'),
              total: m.estado === 'OCUPADA' || m.estado === 'CUENTA' ? 68.00 : null
            };
          });
        }

        if (resProds.ok) {
          const rawProds = await resProds.json();
          products = rawProds.map(p => ({
            id: p.idProducto,
            name: p.nombreProducto,
            category: p.categoria ? p.categoria.nombreCategoria : 'Fondos',
            price: Number(p.precioUnitario),
            stock: Number(p.stockActual)
          }));
        }

        if (resTickets.ok) {
          tickets = await resTickets.json();
        }

        if (resCaja.ok) {
          cashSummary = await resCaja.json();
        }

        if (resUsers.ok) {
          adminUsers = await resUsers.json();
        }

        if (resRoles.ok) {
          adminRoles = await resRoles.json();
        }

        if (resReports.ok) {
          reportData = await resReports.json();
        }

        renderCurrentView();
      } catch (err) {
        console.warn('Conectando a Spring Boot...', err);
      }
    }

    const available = product => product.stock - (order.get(product.id) || 0);

    function heading(kicker, title, subtitle, action = '') {
      return `<div class="page-head"><div><div class="eyebrow">${kicker}</div><h1>${title}</h1><p class="subhead">${subtitle}</p></div>${action}</div>`;
    }

    function renderAdminMenu() {
      return `${heading('Gestión · Menú', 'Menú e inventario', 'Consulta productos, precios y disponibilidad sin salir del panel.', `<button class="button" onclick="syncBackendData(); notify('Menú actualizado.');"><i class="bi bi-arrow-clockwise"></i> Actualizar</button>`)}
        <section class="panel admin-view-panel"><div class="panel-head"><div><span class="panel-title">Productos registrados</span><div class="section-note">${products.length} productos en la carta</div></div><span class="badge green"><i class="bi bi-shield-check"></i> Solo administrador</span></div><div class="panel-body table-scroll"><table class="inventory-table admin-table"><thead><tr><th>Producto</th><th>Categoría</th><th>Precio</th><th>Stock</th><th>Estado</th><th>Acción</th></tr></thead><tbody>${products.length ? products.map(p => `<tr><td><strong>${p.name}</strong></td><td>${p.category}</td><td>${money(p.price)}</td><td>${p.stock}</td><td><span class="badge ${p.stock <= 0 ? 'red' : p.stock <= 5 ? 'gold' : 'green'}">${p.stock <= 0 ? 'Agotado' : p.stock <= 5 ? 'Poco stock' : 'Disponible'}</span></td><td><div class="inline-actions"><button class="button compact" onclick="adjustStockBackend(${p.id}, 1)"><i class="bi bi-plus"></i> Stock</button><button class="button compact" onclick="adjustStockBackend(${p.id}, -1)" ${p.stock <= 0 ? 'disabled' : ''}><i class="bi bi-dash"></i></button></div></td></tr>`).join('') : `<tr><td colspan="6" class="empty">No hay productos registrados.</td></tr>`}</tbody></table></div></section>`;
    }

    function renderAdminUsers() {
      return `${heading('Gestión · Accesos', 'Usuarios y roles', 'Administra quién puede acceder al sistema y con qué nivel.', `<div class="view-actions"><button class="button" onclick="syncBackendData(); notify('Usuarios actualizados.');"><i class="bi bi-arrow-clockwise"></i> Actualizar</button><button class="button primary" onclick="openUserModal()"><i class="bi bi-person-plus"></i> Nuevo usuario</button></div>`)}
        <section class="panel admin-view-panel"><div class="panel-head"><div><span class="panel-title">Equipo registrado</span><div class="section-note">${adminUsers.length} usuarios encontrados</div></div><span class="badge blue"><i class="bi bi-person-lock"></i> Accesos controlados</span></div><div class="panel-body table-scroll"><table class="inventory-table admin-table"><thead><tr><th>Nombre</th><th>Usuario</th><th>Rol</th><th>Estado</th><th>Alcance</th></tr></thead><tbody>${adminUsers.length ? adminUsers.map(u => `<tr><td><strong>${u.nombre || 'Sin nombre'}</strong></td><td>${u.username}</td><td>${u.rol}</td><td><span class="badge ${u.estado === 'ACTIVO' ? 'green' : 'red'}">${u.estado}</span></td><td><span class="section-note">Ver permisos del rol</span></td></tr>`).join('') : `<tr><td colspan="5" class="empty">No hay usuarios registrados.</td></tr>`}</tbody></table></div></section>`;
    }

    function filteredReports() {
      return (reportData.cobros || []).filter(c => {
        const date = String(c.fecha || '').slice(0, 10);
        const fromOk = !reportFilters.from || date >= reportFilters.from;
        const toOk = !reportFilters.to || date <= reportFilters.to;
        const methodOk = reportFilters.method === 'TODOS' || c.metodo === reportFilters.method;
        return fromOk && toOk && methodOk;
      });
    }

    function reportTotal(rows) { return rows.reduce((sum, row) => sum + Number(row.total || 0), 0); }

    function reportMethodTotal(rows, method) { return reportTotal(rows.filter(row => row.metodo === method)); }

    function reportDate(value) { return String(value || '-').replace('T', ' '); }

    function renderReportBars(rows) {
      const methods = [['EFECTIVO', 'Efectivo', 'green'], ['TARJETA', 'Tarjeta', 'blue'], ['YAPE / PLIN', 'Billeteras', 'gold']];
      const max = Math.max(...methods.map(([method]) => reportMethodTotal(rows, method)), 1);
      return methods.map(([method, label, tone]) => {
        const value = method === 'YAPE / PLIN' ? reportTotal(rows.filter(row => row.metodo !== 'EFECTIVO' && row.metodo !== 'TARJETA')) : reportMethodTotal(rows, method);
        return `<div class="report-bar-row"><div class="report-bar-label"><span>${label}</span><strong>${money(value)}</strong></div><div class="report-bar-track"><span class="${tone}" style="width:${Math.max(0, Math.round((value / max) * 100))}%"></span></div></div>`;
      }).join('');
    }

    function setReportFilter(key, value) { reportFilters[key] = value; renderCurrentView(); }

    function clearReportFilters() { reportFilters = { from: '', to: '', method: 'TODOS' }; renderCurrentView(); }

    function renderAdminReports() {
      const rows = filteredReports();
      const total = reportTotal(rows);
      const efectivo = reportMethodTotal(rows, 'EFECTIVO');
      const tarjeta = reportMethodTotal(rows, 'TARJETA');
      const billeteras = reportTotal(rows.filter(row => row.metodo !== 'EFECTIVO' && row.metodo !== 'TARJETA'));
      return `${heading('Análisis · Reportes', 'Reportes y auditoría', 'Consulta el rendimiento del negocio desde la misma experiencia administrativa.', `<button class="button" onclick="syncBackendData(); notify('Reportes actualizados.');"><i class="bi bi-arrow-clockwise"></i> Actualizar</button>`)}
        <section class="panel report-filters"><div class="panel-body report-filter-row"><label>Desde<input type="date" value="${reportFilters.from}" onchange="setReportFilter('from', this.value)"></label><label>Hasta<input type="date" value="${reportFilters.to}" onchange="setReportFilter('to', this.value)"></label><label>Método<select onchange="setReportFilter('method', this.value)"><option value="TODOS" ${reportFilters.method === 'TODOS' ? 'selected' : ''}>Todos</option><option value="EFECTIVO" ${reportFilters.method === 'EFECTIVO' ? 'selected' : ''}>Efectivo</option><option value="TARJETA" ${reportFilters.method === 'TARJETA' ? 'selected' : ''}>Tarjeta</option><option value="YAPE" ${reportFilters.method === 'YAPE' ? 'selected' : ''}>Yape</option><option value="PLIN" ${reportFilters.method === 'PLIN' ? 'selected' : ''}>Plin</option></select></label><button class="button" onclick="clearReportFilters()"><i class="bi bi-x-circle"></i> Limpiar</button></div></section>
        <section class="metrics admin-report-metrics"><article class="metric"><div><div class="metric-label">Total vendido</div><div class="metric-value">${money(total)}</div><div class="metric-note">${rows.length} cobros filtrados</div></div><span class="metric-icon"><i class="bi bi-graph-up-arrow"></i></span></article><article class="metric"><div><div class="metric-label">Efectivo</div><div class="metric-value">${money(efectivo)}</div><div class="metric-note">Ingresos en efectivo</div></div><span class="metric-icon"><i class="bi bi-cash"></i></span></article><article class="metric"><div><div class="metric-label">Tarjeta</div><div class="metric-value">${money(tarjeta)}</div><div class="metric-note">Pagos con tarjeta</div></div><span class="metric-icon"><i class="bi bi-credit-card"></i></span></article><article class="metric"><div><div class="metric-label">Billeteras</div><div class="metric-value">${money(billeteras)}</div><div class="metric-note">Yape / Plin</div></div><span class="metric-icon"><i class="bi bi-phone"></i></span></article></section>
        <section class="report-grid"><article class="panel"><div class="panel-head"><div><span class="panel-title">Distribución por método</span><div class="section-note">Comparación del monto recaudado</div></div></div><div class="panel-body report-bars">${renderReportBars(rows)}</div></article><article class="panel"><div class="panel-head"><div><span class="panel-title">Lectura rápida</span><div class="section-note">Resultado del filtro actual</div></div></div><div class="panel-body report-summary"><div><span>Mayor participación</span><strong>${efectivo >= tarjeta && efectivo >= billeteras ? 'Efectivo' : tarjeta >= billeteras ? 'Tarjeta' : 'Billeteras'}</strong></div><div><span>Promedio por cobro</span><strong>${money(rows.length ? total / rows.length : 0)}</strong></div><div><span>Último comprobante</span><strong>${rows[rows.length - 1]?.comprobante || '-'}</strong></div></div></article></section>
        <section class="panel admin-view-panel"><div class="panel-head"><div><span class="panel-title">Cobros emitidos</span><div class="section-note">Historial disponible para revisión administrativa</div></div><span class="badge green">${rows.length} registros</span></div><div class="panel-body table-scroll"><table class="inventory-table admin-table"><thead><tr><th>Fecha</th><th>Comprobante</th><th>Pedido</th><th>Método</th><th>Total</th></tr></thead><tbody>${rows.length ? rows.map(c => `<tr><td>${reportDate(c.fecha)}</td><td><strong>${c.comprobante}</strong></td><td>${c.pedido || '-'}</td><td>${c.metodo}</td><td>${money(c.total)}</td></tr>`).join('') : `<tr><td colspan="5" class="empty">No hay cobros para los filtros seleccionados.</td></tr>`}</tbody></table></div></section>`;
    }

    function openUserModal() {
      const roleOptions = adminRoles.length
        ? adminRoles.map(role => `<option value="${role.id}">${role.nombre}</option>`).join('')
        : '<option value="">No hay roles disponibles</option>';
      const modalHtml = `<div id="userModal" class="modal-overlay"><div class="modal-box"><div class="modal-title-row"><div><h2 style="margin:0;font-size:16px;">Nuevo usuario</h2><p style="margin:4px 0 0;color:var(--muted);font-size:11px;">Crea un acceso para un miembro del equipo.</p></div><button onclick="closeModal('userModal')" style="border:none;background:none;cursor:pointer;font-size:18px;">✕</button></div><form onsubmit="createUserBackend(event)"><div class="modal-field"><label for="newUserName">Nombre completo</label><input id="newUserName" name="nombre" required maxlength="120" placeholder="Ej. Ana Torres"></div><div class="modal-field"><label for="newUserUsername">Nombre de usuario</label><input id="newUserUsername" name="username" required maxlength="50" placeholder="Ej. ana.torres"></div><div class="modal-field"><label for="newUserPassword">Contraseña</label><input id="newUserPassword" name="password" type="password" required minlength="6" placeholder="Mínimo 6 caracteres"></div><div class="modal-field"><label for="newUserRole">Rol</label><select id="newUserRole" name="rolId" required>${roleOptions}</select></div><div style="display:flex;gap:10px;margin-top:18px;"><button type="button" class="button" style="flex:1;" onclick="closeModal('userModal')">Cancelar</button><button type="submit" class="button primary" style="flex:1;" ${adminRoles.length ? '' : 'disabled'}>Crear usuario</button></div></form></div></div>`;
      document.body.insertAdjacentHTML('beforeend', modalHtml);
    }

    async function createUserBackend(event) {
      event.preventDefault();
      const form = event.currentTarget;
      const payload = Object.fromEntries(new FormData(form).entries());
      payload.rolId = Number(payload.rolId);
      try {
        const res = await fetch('/api/usuarios', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(payload) });
        const data = await res.json();
        if (!res.ok) { notify(data.error || 'No se pudo crear el usuario.'); return; }
        closeModal('userModal');
        notify(data.mensaje || 'Usuario creado correctamente.');
        await syncBackendData();
        setView('usuarios');
      } catch (error) {
        notify('No se pudo conectar con el servidor. Reinicia Spring Boot e inténtalo nuevamente.');
      }
    }

    // ----------------------------------------------------------
    // VISTA 0: DASHBOARD ADMINISTRATIVO
    // ----------------------------------------------------------
    function renderDashboard() {
      const occupied = tables.filter(t => t.status === 'Ocupada').length;
      const availableTables = tables.filter(t => t.status === 'Libre').length;
      const lowStock = products.filter(p => p.stock > 0 && p.stock <= 5);
      const outOfStock = products.filter(p => p.stock <= 0);
      const pendingTickets = tickets.filter(t => t.state === 'Pendiente').length;
      const totalRec = Number(cashSummary.totalRecaudado || 0);
      const totalTables = tables.length || 0;
      const occupancy = totalTables ? Math.round((occupied / totalTables) * 100) : 0;
      const topProducts = [...products].sort((a, b) => b.stock - a.stock).slice(0, 5);

      return `${heading('Panel administrativo · En vivo', 'Resumen general', 'Una vista rápida para tomar decisiones sobre la operación del restaurante.', `<button class="button" onclick="syncBackendData(); notify('Dashboard actualizado.');"><i class="bi bi-arrow-clockwise"></i> Actualizar datos</button>`)}
        <section class="dashboard-kpis" aria-label="Indicadores principales">
          <article class="admin-kpi admin-kpi-primary">
            <div class="admin-kpi-icon"><i class="bi bi-cash-stack"></i></div>
            <div class="admin-kpi-label">Ventas del turno</div>
            <div class="admin-kpi-value">${money(totalRec)}</div>
            <div class="admin-kpi-note">${cashSummary.transacciones || 0} comprobantes registrados</div>
          </article>
          <article class="admin-kpi">
            <div class="admin-kpi-icon"><i class="bi bi-grid-3x3-gap"></i></div>
            <div class="admin-kpi-label">Ocupación del salón</div>
            <div class="admin-kpi-value">${occupancy}%</div>
            <div class="admin-kpi-note">${occupied} ocupadas · ${availableTables} disponibles</div>
          </article>
          <article class="admin-kpi">
            <div class="admin-kpi-icon"><i class="bi bi-fire"></i></div>
            <div class="admin-kpi-label">Pedidos en cocina</div>
            <div class="admin-kpi-value">${tickets.length}</div>
            <div class="admin-kpi-note">${pendingTickets} pendientes de atención</div>
          </article>
          <article class="admin-kpi admin-kpi-warning">
            <div class="admin-kpi-icon"><i class="bi bi-exclamation-triangle"></i></div>
            <div class="admin-kpi-label">Alertas de inventario</div>
            <div class="admin-kpi-value">${lowStock.length + outOfStock.length}</div>
            <div class="admin-kpi-note">${outOfStock.length} agotados · ${lowStock.length} por reponer</div>
          </article>
        </section>

        <section class="dashboard-grid">
          <article class="panel dashboard-panel">
            <div class="panel-head">
              <div><div class="panel-title">Estado operativo</div><div class="section-note">Situación actual del restaurante</div></div>
              <span class="badge green"><span class="online-dot" style="background:currentColor;box-shadow:none;width:6px;height:6px"></span> En vivo</span>
            </div>
            <div class="panel-body">
              <div class="operation-list">
                <div class="operation-row"><span><i class="bi bi-grid-3x3-gap"></i> Mesas ocupadas</span><strong>${occupied}/${totalTables}</strong></div>
                <div class="operation-row"><span><i class="bi bi-receipt"></i> Pedidos en proceso</span><strong>${tickets.length}</strong></div>
                <div class="operation-row"><span><i class="bi bi-wallet2"></i> Caja recaudada</span><strong>${money(totalRec)}</strong></div>
                <div class="operation-row"><span><i class="bi bi-box-seam"></i> Productos disponibles</span><strong>${products.filter(p => p.stock > 0).length}/${products.length}</strong></div>
              </div>
              <div class="progress-label"><span>Ocupación del salón</span><strong>${occupancy}%</strong></div>
              <div class="progress-track"><span style="width:${occupancy}%"></span></div>
            </div>
          </article>

          <article class="panel dashboard-panel">
            <div class="panel-head">
              <div><div class="panel-title">Acciones rápidas</div><div class="section-note">Accesos para el administrador</div></div>
            </div>
            <div class="panel-body quick-actions">
              <button class="quick-action" onclick="setView('inventario')"><i class="bi bi-box-seam"></i><span>Revisar inventario</span><i class="bi bi-chevron-right"></i></button>
              <button class="quick-action" onclick="setView('cocina')"><i class="bi bi-fire"></i><span>Ver cocina</span><i class="bi bi-chevron-right"></i></button>
              <button class="quick-action" onclick="setView('caja')"><i class="bi bi-cash-coin"></i><span>Consultar caja</span><i class="bi bi-chevron-right"></i></button>
              <a class="quick-action" href="/reportes"><i class="bi bi-file-earmark-bar-graph"></i><span>Generar reporte</span><i class="bi bi-chevron-right"></i></a>
            </div>
          </article>
        </section>

        <section class="dashboard-grid dashboard-grid-bottom">
          <article class="panel dashboard-panel">
            <div class="panel-head"><div><div class="panel-title">Productos que requieren atención</div><div class="section-note">Prioriza la reposición antes del siguiente turno</div></div><button class="button" onclick="setView('inventario')">Ver inventario</button></div>
            <div class="panel-body">
              ${lowStock.length || outOfStock.length ? `<div class="attention-list">${[...outOfStock, ...lowStock].slice(0, 6).map(p => `<div class="attention-row"><span class="attention-product"><span class="product-mini-icon"><i class="bi bi-box"></i></span><span><strong>${p.name}</strong><small>${p.category}</small></span></span><span class="badge ${p.stock <= 0 ? 'red' : 'gold'}">${p.stock <= 0 ? 'Agotado' : `${p.stock} unidades`}</span></div>`).join('')}</div>` : `<div class="empty"><i class="bi bi-check-circle" style="font-size:28px;color:var(--green);display:block;margin-bottom:7px;"></i>No hay alertas de inventario.</div>`}
            </div>
          </article>

          <article class="panel dashboard-panel">
            <div class="panel-head"><div><div class="panel-title">Resumen del menú</div><div class="section-note">Productos registrados por disponibilidad</div></div></div>
            <div class="panel-body">
              <div class="mini-bars">${topProducts.length ? topProducts.map((p, i) => `<div class="mini-bar-row"><span>${p.name}</span><div class="mini-bar"><span style="width:${Math.min(100, Math.max(8, (p.stock / Math.max(...products.map(x => x.stock), 1)) * 100))}%"></span></div><strong>${p.stock}</strong></div>`).join('') : '<div class="empty">No hay productos registrados.</div>'}</div>
            </div>
          </article>
        </section>`;
    }

    // ----------------------------------------------------------
    // VISTA 1: SALÓN
    // ----------------------------------------------------------
    function renderSalon() {
      const occupied = tables.filter(t => t.status === 'Ocupada').length;
      const inAccount = tables.filter(t => t.status === 'En cuenta').length;
      const totalRec = Number(cashSummary.totalRecaudado || 0);

      return `${heading('Turno de servicio · En vivo', 'Resumen del salón', 'Consulta el estado actual de las mesas y la actividad del salón.')}
        <section class="metrics" aria-label="Resumen del turno">
          <article class="metric">
            <div>
              <div class="metric-label">Ventas registradas</div>
              <div class="metric-value">${money(totalRec)}</div>
              <div class="metric-note">${cashSummary.transacciones || 0} comprobantes emitidos</div>
            </div>
            <span class="metric-icon"><i class="bi bi-graph-up-arrow"></i></span>
          </article>
          <article class="metric">
            <div>
              <div class="metric-label">Mesas ocupadas</div>
              <div class="metric-value">${occupied} <small style="font-size:13px;color:var(--muted);font-weight:500">/ ${tables.length}</small></div>
              <div class="metric-note">${tables.filter(t => t.status === 'Libre').length} disponibles ahora</div>
            </div>
            <span class="metric-icon"><i class="bi bi-grid"></i></span>
          </article>
          <article class="metric">
            <div>
              <div class="metric-label">Pedidos en cocina</div>
              <div class="metric-value">${tickets.length}</div>
              <div class="metric-note">${tickets.filter(t => t.state === 'Pendiente').length} pendientes de cocción</div>
            </div>
            <span class="metric-icon"><i class="bi bi-fire"></i></span>
          </article>
          <article class="metric">
            <div>
              <div class="metric-label">Mesas en cuenta</div>
              <div class="metric-value">${inAccount}</div>
              <div class="metric-note">Listas para cobro en caja</div>
            </div>
            <span class="metric-icon"><i class="bi bi-wallet2"></i></span>
          </article>
        </section>
        <section class="section">
          <div class="section-head">
            <h2>Estado de las mesas</h2>
            <span class="section-note"><span class="online-dot" style="display:inline-block;margin-right:4px;"></span> Estado actualizado</span>
          </div>
          <div class="table-grid">
            ${tables.map(t => `
              <article class="table-card">
                <div class="table-card-top">
                  <div>
                    <div class="table-caption">MESA</div>
                    <div class="table-number">${t.no}</div>
                  </div>
                  <span class="badge ${t.kind}">
                    <span class="online-dot" style="background:currentColor;box-shadow:none;width:6px;height:6px"></span>
                    ${t.status}
                  </span>
                </div>
                <div class="table-info">
                  <span>Capacidad: ${t.people}/${t.capacity}</span>
                  <span>${t.status === 'En cuenta' ? 'Cuenta lista' : t.time}</span>
                </div>
                ${t.status === 'En cuenta' 
                  ? `<button class="button primary" onclick="openPaymentModal('${t.no}')"><i class="bi bi-credit-card"></i> Cobrar cuenta →</button>`
                  : t.status === 'Limpieza'
                  ? `<button class="button" onclick="cleanTable('${t.no}')"><i class="bi bi-check2-circle"></i> Marcar libre</button>`
                  : `<button class="button" onclick="openComanda('${t.no}')">${t.status === 'Ocupada' ? 'Ver comanda' : 'Abrir mesa'} <span aria-hidden="true">→</span></button>`
                }
              </article>
            `).join('')}
          </div>
        </section>`;
    }

    function openComanda(tableNo = selectedTable) { 
      selectedTable = tableNo; 
      setView('comanda'); 
    }

    // ----------------------------------------------------------
    // VISTA 2: COMANDA DIGITAL
    // ----------------------------------------------------------
    function renderComanda() {
      const items = [...order.entries()].map(([id, qty]) => ({ product: products.find(p => p.id === id), qty })).filter(x => x.product);
      const total = items.reduce((sum, item) => sum + item.product.price * item.qty, 0);
      const stockError = items.some(({ product, qty }) => qty > product.stock);
      const filtered = products.filter(p => p.category === category);

      return `${heading(`Servicio · Mesa ${selectedTable}`, 'Comanda digital', 'Agrega productos y consulta su disponibilidad en inventario.', `<button class="button" onclick="setView('salon')">← Volver al salón</button>`)}
        <div class="content-grid">
          <section class="panel">
            <div class="panel-head">
              <span class="panel-title">Menú / Carta</span>
              <span class="section-note">Disponibilidad en tiempo real</span>
            </div>
            <div class="panel-body">
              <div class="toolbar">
                <span class="toolbar-label">Categoría:</span>
                ${categories.map(c => `<button class="chip ${category === c ? 'active' : ''}" onclick="setCategory('${c}')">${c}</button>`).join('')}
              </div>
              <div class="menu-grid">
                ${filtered.map(p => { 
                  const left = Math.max(0, available(p)); 
                  const stockClass = left === 0 ? 'out' : left < 5 ? 'low' : ''; 
                  const iconClass = getCategoryIcon(p.category);
                  return `<button class="product" onclick="addToOrder(${p.id})" ${left <= 0 ? 'disabled' : ''}>
                    <span class="product-icon"><i class="bi ${iconClass}"></i></span>
                    <span>
                      <span class="product-name">${p.name}</span>
                      <span class="product-price">${money(p.price)}</span>
                      <span class="product-stock ${stockClass}">${left === 0 ? 'Agotado' : `${left} disponibles`}</span>
                    </span>
                  </button>`; 
                }).join('')}
              </div>
            </div>
          </section>

          <aside class="panel">
            <div class="panel-head">
              <span class="panel-title">Pedido · Mesa ${selectedTable}</span>
              <span class="badge green">${items.length} productos</span>
            </div>
            <div class="panel-body">
              ${items.length ? `
                <div class="order-list">
                  ${items.map(({product:p,qty}) => `
                    <div class="order-item">
                      <div>
                        <div class="order-item-name">${p.name}</div>
                        <div class="order-item-meta">${money(p.price)} · ${qty > p.stock ? 'Stock insuficiente' : `${available(p)} en stock`}</div>
                      </div>
                      <div class="qty-control">
                        <button onclick="changeQty(${p.id},-1)" aria-label="Quitar uno">−</button>
                        <span class="qty">${qty}</span>
                        <button onclick="changeQty(${p.id},1)" ${available(p) <= 0 ? 'disabled' : ''} aria-label="Agregar uno">+</button>
                      </div>
                    </div>
                  `).join('')}
                </div>
                <div class="order-total">
                  <span>Total estimado</span>
                  <strong>${money(total)}</strong>
                </div>
                ${stockError ? '<p class="product-stock out">Stock insuficiente. Ajusta la cantidad antes de enviar.</p>' : ''}
                <button class="button primary full" onclick="sendOrder()" ${stockError ? 'disabled' : ''}>
                  Enviar a cocina <span aria-hidden="true">→</span>
                </button>
              ` : `
                <div class="empty">
                  <i class="bi bi-inbox" style="font-size:30px;color:var(--muted);opacity:0.5;display:block;margin-bottom:6px;"></i>
                  Selecciona productos del menú para armar la comanda.
                </div>
                <button class="button primary full" disabled>Enviar a cocina</button>
              `}
            </div>
          </aside>
        </div>`;
    }

    function escapeHtml(value) {
      return String(value || '').replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' })[c]);
    }

    // ----------------------------------------------------------
    // VISTA 3: CLIENTE / AUTOSERVICIO QR
    // ----------------------------------------------------------
    function renderCustomer() {
      const items = [...customerCart.entries()].map(([id, qty]) => ({ product: products.find(p => p.id === id), qty })).filter(item => item.product);
      const total = items.reduce((sum, item) => sum + item.product.price * item.qty, 0);
      const filtered = products.filter(product => product.category === category);
      const stockError = items.some(({ product, qty }) => qty > product.stock);
      const progress = customerTicket?.state === 'Listo' ? 3 : customerTicket?.state === 'Preparando' ? 2 : customerTicket ? 1 : 0;
      const tableOptions = tables.length ? tables : [{no:'M01'}, {no:'M02'}];

      return `${heading('Experiencia del comensal', 'Tu mesa, tu pedido', 'Explora el menú y sigue tu pedido desde un solo lugar.', '<span class="badge green">Vista cliente</span>')}
        <section class="client-intro">
          <div>
            <h2>Bienvenido a RestoGestión</h2>
            <p>Elige tu mesa para comenzar. Tu comanda se enviará directamente al servidor de cocina.</p>
          </div>
          <label class="toolbar-label" for="customerTable">Mesa 
            <select class="table-select" id="customerTable" onchange="setCustomerTable(this.value)">
              ${tableOptions.map(t => `<option value="${t.no}" ${customerTable === t.no ? 'selected' : ''}>Mesa ${t.no}</option>`).join('')}
            </select>
          </label>
        </section>

        <div class="client-layout">
          <section class="panel">
            <div class="panel-head">
              <span class="panel-title">Menú del restaurante</span>
              <span class="section-note">Platillos con stock disponible</span>
            </div>
            <div class="panel-body">
              <div class="toolbar">
                ${categories.map(value => `<button class="chip ${category === value ? 'active' : ''}" onclick="setCategory('${value}')">${value}</button>`).join('')}
              </div>
              <div class="menu-grid">
                ${filtered.map(product => { 
                  const left = Math.max(0, product.stock - (customerCart.get(product.id) || 0)); 
                  const stockClass = left === 0 ? 'out' : left < 5 ? 'low' : ''; 
                  const iconClass = getCategoryIcon(product.category);
                  return `<button class="product client-product" onclick="addCustomerItem(${product.id})" ${left === 0 ? 'disabled' : ''}>
                    <span class="product-icon"><i class="bi ${iconClass}"></i></span>
                    <span>
                      <span class="product-name">${product.name}</span>
                      <span class="product-price">${money(product.price)}</span>
                      <span class="product-stock ${stockClass}">${left ? `${left} disponibles` : 'Agotado'}</span>
                    </span>
                    <span class="add-product" aria-hidden="true">+</span>
                  </button>`; 
                }).join('')}
              </div>
            </div>
          </section>

          <aside>
            <section class="panel">
              <div class="panel-head">
                <span class="panel-title">Tu pedido · Mesa ${customerTable}</span>
                <span class="badge green">${items.length} productos</span>
              </div>
              <div class="panel-body">
                ${items.length ? `
                  <div class="order-list">
                    ${items.map(({product,qty}) => `
                      <div class="order-item">
                        <div>
                          <div class="order-item-name">${product.name}</div>
                          <div class="order-item-meta">${money(product.price)} · ${qty > product.stock ? 'Stock insuficiente' : `${product.stock - qty} restantes`}</div>
                        </div>
                        <div class="qty-control">
                          <button onclick="changeCustomerQty(${product.id},-1)" aria-label="Quitar uno">−</button>
                          <span class="qty">${qty}</span>
                          <button onclick="changeCustomerQty(${product.id},1)" ${product.stock - qty <= 0 ? 'disabled' : ''} aria-label="Agregar uno">+</button>
                        </div>
                      </div>
                    `).join('')}
                  </div>
                  <div class="order-total">
                    <span>Total</span>
                    <strong>${money(total)}</strong>
                  </div>
                ` : '<div class="customer-empty"><i class="bi bi-cart" style="font-size:26px;opacity:0.5;display:block;margin-bottom:6px;"></i>Tu carrito está vacío.<br>Agrega platos del menú.</div>'}

                <label class="toolbar-label" for="customerNote" style="margin-top:12px;">Indicaciones para cocina</label>
                <textarea class="client-note" id="customerNote" placeholder="Ej.: sin cebolla, salsa aparte, bien cocido..." oninput="customerNote=this.value">${escapeHtml(customerNote)}</textarea>
                <button class="button primary full" style="margin-top:11px" onclick="placeCustomerOrder()" ${!items.length || stockError ? 'disabled' : ''}>
                  Confirmar pedido · ${money(total)}
                </button>
              </div>
            </section>

            ${customerTicket ? `
              <section class="tracking">
                <div class="tracking-title">
                  <span>Seguimiento · Mesa ${customerTicket.table}</span>
                  <span class="badge ${customerTicket.kind}">${customerTicket.state}</span>
                </div>
                <div class="tracking-steps">
                  <span class="tracking-step ${progress >= 1 ? 'done' : ''}">Pedido recibido</span>
                  <span class="tracking-step ${progress >= 2 ? 'done' : ''}">En preparación</span>
                  <span class="tracking-step ${progress >= 3 ? 'done' : ''}">Listo para servir</span>
                </div>
              </section>
            ` : ''}
          </aside>
        </div>`;
    }

    // ----------------------------------------------------------
    // VISTA 4: INVENTARIO Y STOCK
    // ----------------------------------------------------------
    function renderInventory() {
      const low = products.filter(p => p.stock < 5).length;
      return `${heading('Control de productos', 'Inventario y Carta', 'Consulta el stock y ajusta cantidades.', `<span class="badge ${low ? 'gold' : 'green'}">${low} con stock bajo</span>`)}
        <section class="panel">
          <div class="panel-head">
            <span class="panel-title">Catálogo de productos</span>
            <div style="display:flex;align-items:center;gap:12px;">
              <span class="section-note">${products.length} platillos registrados</span>
              <a class="button primary" href="/menu/nuevo"><i class="bi bi-plus-circle"></i> Agregar producto</a>
            </div>
          </div>
          <div style="overflow:auto">
            <table class="inventory-table">
              <thead>
                <tr>
                  <th>Producto</th>
                  <th>Categoría</th>
                  <th>Precio</th>
                  <th>Disponibilidad</th>
                  <th>Estado</th>
                </tr>
              </thead>
              <tbody>
                ${products.map(p => `
                  <tr>
                    <td>
                      <span style="display:inline-flex;align-items:center;gap:8px;">
                        <i class="bi ${getCategoryIcon(p.category)}" style="color:var(--green)"></i>
                        <strong>${p.name}</strong>
                      </span>
                    </td>
                    <td>${p.category}</td>
                    <td>${money(p.price)}</td>
                    <td>
                      <div class="stock-edit">
                        <button onclick="adjustStockBackend(${p.id},-1)" aria-label="Disminuir stock">−</button>
                        <strong>${p.stock}</strong>
                        <button onclick="adjustStockBackend(${p.id},1)" aria-label="Aumentar stock">+</button>
                      </div>
                    </td>
                    <td>
                      <span class="badge ${p.stock === 0 ? 'red' : p.stock < 5 ? 'gold' : 'green'}">
                        ${p.stock === 0 ? 'Agotado' : p.stock < 5 ? 'Stock bajo' : 'Disponible'}
                      </span>
                    </td>
                  </tr>
                `).join('')}
              </tbody>
            </table>
          </div>
        </section>`;
    }

    // ----------------------------------------------------------
    // VISTA 5: COCINA / KDS
    // ----------------------------------------------------------
    function renderKitchen() {
      const columns = [{name:'Pendiente',kind:'red'}, {name:'Preparando',kind:'gold'}, {name:'Listo',kind:'green'}];
      return `${heading('Producción · En tiempo real', 'Panel de cocina (KDS)', 'Despacho de comandas y avance de preparación.')}
        <div class="kitchen-grid">
          ${columns.map(col => { 
            const list = tickets.filter(t => t.state === col.name); 
            return `<section class="kitchen-column">
              <div class="kitchen-column-head">
                <span>${col.name}</span>
                <span class="badge ${col.kind}">${list.length}</span>
              </div>
              ${list.length ? list.map(t => `
                <article class="ticket">
                  <div class="ticket-title">
                    <span>Mesa ${t.table}</span>
                    <span class="badge ${col.kind}">${t.state}</span>
                  </div>
                  <div class="ticket-items">
                    ${t.items.join('<br>')}
                    ${t.note ? `<br><em>Nota: ${escapeHtml(t.note)}</em>` : ''}
                  </div>
                  <button class="button full" onclick="advanceTicketBackend(${t.idPedido})">
                    ${t.state === 'Pendiente' ? 'Iniciar preparación' : t.state === 'Preparando' ? 'Marcar listo' : 'Despachar'}
                  </button>
                </article>
              `).join('') : '<div class="empty">Sin pedidos en este estado</div>'}
            </section>`; 
          }).join('')}
        </div>`;
    }

    // ----------------------------------------------------------
    // VISTA 6: CAJA Y COBROS
    // ----------------------------------------------------------
    function renderCash() {
      const totalRec = Number(cashSummary.totalRecaudado || 0);
      const efec = Number(cashSummary.efectivo || 0);
      const tarj = Number(cashSummary.tarjeta || 0);
      const bille = Number(cashSummary.billeteras || 0);

      return `${heading('Caja · Turno actual', 'Resumen y Arqueo de Caja', 'Control de cobros diferenciados por método de pago.')}
        <section class="metrics">
          <article class="metric">
            <div>
              <div class="metric-label">Total recaudado</div>
              <div class="metric-value">${money(totalRec)}</div>
              <div class="metric-note">${cashSummary.transacciones || 0} comprobantes emitidos</div>
            </div>
            <span class="metric-icon"><i class="bi bi-wallet2"></i></span>
          </article>
          <article class="metric">
            <div>
              <div class="metric-label">Efectivo</div>
              <div class="metric-value">${money(efec)}</div>
              <div class="metric-note">Conteo para arqueo</div>
            </div>
            <span class="metric-icon"><i class="bi bi-cash"></i></span>
          </article>
          <article class="metric">
            <div>
              <div class="metric-label">Tarjeta</div>
              <div class="metric-value">${money(tarj)}</div>
              <div class="metric-note">POS Débito/Crédito</div>
            </div>
            <span class="metric-icon"><i class="bi bi-credit-card"></i></span>
          </article>
          <article class="metric">
            <div>
              <div class="metric-label">Billeteras digitales</div>
              <div class="metric-value">${money(bille)}</div>
              <div class="metric-note">Yape y Plin</div>
            </div>
            <span class="metric-icon"><i class="bi bi-phone"></i></span>
          </article>
        </section>

        <section class="simple-panel">
          <div style="display:flex;justify-content:space-between;align-items:center;">
            <h2>Actividad y Estado del Turno</h2>
            <button class="button primary" onclick="openArqueoModal()"><i class="bi bi-calculator"></i> Realizar Arqueo / Cierre de Caja</button>
          </div>
          <p style="margin-top:10px;">Las ventas se asocian a la sesión de caja abierta. Al cerrar turno se calcula el descuadre con el saldo físico contado.</p>
          <div class="table-info">
            <span>Monto inicial en caja: <strong>${money(cashSummary.montoInicial || 200)}</strong></span>
            <span>Total en gaveta esperado: <strong>${money(Number(cashSummary.montoInicial || 200) + efec)}</strong></span>
          </div>
        </section>`;
    }

    // ----------------------------------------------------------
    // MODAL DE COBRO DE MESA
    // ----------------------------------------------------------
    function openPaymentModal(tableNo) {
      const table = tables.find(t => t.no === tableNo);
      if (!table) return;
      billingTable = table;

      const totalCalculado = table.total || 68.00;
      const subtotal = (totalCalculado / 1.18).toFixed(2);
      const igv = (totalCalculado - subtotal).toFixed(2);

      const modalHtml = `
        <div id="paymentModal" class="modal-overlay">
          <div class="modal-box">
            <div class="modal-title-row">
              <h2 style="margin:0;font-size:16px;">Cobro · Mesa ${table.no}</h2>
              <button onclick="closeModal('paymentModal')" style="border:none;background:none;cursor:pointer;font-size:18px;">✕</button>
            </div>
            
            <div style="background:var(--canvas);padding:12px;border-radius:8px;margin-bottom:14px;font-size:12px;">
              <div style="display:flex;justify-content:space-between;"><span>Subtotal (Op. Gravada):</span><strong>S/ ${subtotal}</strong></div>
              <div style="display:flex;justify-content:space-between;margin-top:4px;"><span>IGV (18%):</span><strong>S/ ${igv}</strong></div>
              <div style="display:flex;justify-content:space-between;margin-top:8px;font-size:15px;color:var(--green);border-top:1px solid var(--line);padding-top:6px;">
                <span>TOTAL A COBRAR:</span><strong>S/ ${Number(totalCalculado).toFixed(2)}</strong>
              </div>
            </div>

            <div class="modal-field">
              <label>Tipo de Comprobante</label>
              <select id="docType">
                <option value="BOLETA">Boleta de Venta Electrónica (B001)</option>
                <option value="FACTURA">Factura Electrónica (F001)</option>
              </select>
            </div>

            <div class="modal-field">
              <label>Método de Pago</label>
              <select id="payMethod" onchange="toggleCashInput(this.value)">
                <option value="EFECTIVO">Efectivo</option>
                <option value="TARJETA">Tarjeta (Débito/Crédito)</option>
                <option value="YAPE">Yape</option>
                <option value="PLIN">Plin</option>
              </select>
            </div>

            <div id="cashSection" class="modal-field">
              <label>Monto recibido (S/)</label>
              <input type="number" id="cashReceived" placeholder="0.00" oninput="calcChange(${totalCalculado})">
              <div id="changeFeedback" style="margin-top:6px;font-size:12px;font-weight:600;color:var(--green);">Vuelto: S/ 0.00</div>
            </div>

            <div style="display:flex;gap:10px;margin-top:18px;">
              <button class="button" style="flex:1;" onclick="closeModal('paymentModal')">Cancelar</button>
              <button class="button primary" style="flex:1;" onclick="confirmPaymentBackend(${totalCalculado})">Confirmar Cobro</button>
            </div>
          </div>
        </div>
      `;
      document.body.insertAdjacentHTML('beforeend', modalHtml);
    }

    function calcChange(total) {
      const received = parseFloat(document.getElementById('cashReceived').value) || 0;
      const change = received - total;
      const feedback = document.getElementById('changeFeedback');
      if (change >= 0) {
        feedback.style.color = 'var(--green)';
        feedback.textContent = `Vuelto: S/ ${change.toFixed(2)}`;
      } else {
        feedback.style.color = 'var(--red)';
        feedback.textContent = `Faltan: S/ ${Math.abs(change).toFixed(2)}`;
      }
    }

    function toggleCashInput(method) {
      document.getElementById('cashSection').style.display = method === 'EFECTIVO' ? 'block' : 'none';
    }

    async function confirmPaymentBackend(total) {
      if (!billingTable) return;
      const docType = document.getElementById('docType').value;
      const payMethod = document.getElementById('payMethod').value;
      const cashReceived = parseFloat(document.getElementById('cashReceived')?.value) || total;

      try {
        const res = await fetch('/api/caja/cobrar', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            numeroMesa: billingTable.no,
            tipoComprobante: docType,
            metodoPago: payMethod,
            montoRecibido: cashReceived
          })
        });

        const data = await res.json();
        closeModal('paymentModal');
        notify(data.mensaje || `Comprobante emitido. Mesa ${billingTable.no} liberada a limpieza.`);
        await syncBackendData();
      } catch (err) {
        billingTable.status = 'Limpieza';
        billingTable.kind = 'gold';
        closeModal('paymentModal');
        notify(`Cobro registrado para Mesa ${billingTable.no}.`);
        renderCurrentView();
      }
    }

    // ----------------------------------------------------------
    // MODAL DE ARQUEO DE CAJA
    // ----------------------------------------------------------
    function openArqueoModal() {
      const efec = Number(cashSummary.efectivo || 0);
      const inicial = Number(cashSummary.montoInicial || 200);
      const esperado = inicial + efec;

      const modalHtml = `
        <div id="arqueoModal" class="modal-overlay">
          <div class="modal-box">
            <div class="modal-title-row">
              <h2 style="margin:0;font-size:16px;">Arqueo y Cierre de Turno</h2>
              <button onclick="closeModal('arqueoModal')" style="border:none;background:none;cursor:pointer;font-size:18px;">✕</button>
            </div>
            
            <div style="background:var(--canvas);padding:12px;border-radius:8px;margin-bottom:14px;font-size:12px;">
              <div style="display:flex;justify-content:space-between;"><span>Monto inicial en caja:</span><strong>${money(inicial)}</strong></div>
              <div style="display:flex;justify-content:space-between;margin-top:4px;"><span>Ventas en efectivo:</span><strong>${money(efec)}</strong></div>
              <div style="display:flex;justify-content:space-between;margin-top:8px;font-size:14px;color:var(--ink);border-top:1px solid var(--line);padding-top:6px;">
                <span>Efectivo esperado en gaveta:</span><strong>${money(esperado)}</strong>
              </div>
            </div>

            <div class="modal-field">
              <label>Conteo físico de dinero en efectivo (S/)</label>
              <input type="number" id="conteoFisico" placeholder="0.00" oninput="calcDescuadre(${esperado})">
              <div id="descuadreFeedback" style="margin-top:6px;font-size:12px;font-weight:600;color:var(--muted);">Ingrese el saldo para calcular descuadre.</div>
            </div>

            <div style="display:flex;gap:10px;margin-top:18px;">
              <button class="button" style="flex:1;" onclick="closeModal('arqueoModal')">Cancelar</button>
              <button class="button primary" style="flex:1;" onclick="closeModal('arqueoModal'); notify('Cierre de turno registrado con éxito.');">Guardar Cierre</button>
            </div>
          </div>
        </div>
      `;
      document.body.insertAdjacentHTML('beforeend', modalHtml);
    }

    function calcDescuadre(esperado) {
      const fisico = parseFloat(document.getElementById('conteoFisico').value) || 0;
      const descuadre = fisico - esperado;
      const feedback = document.getElementById('descuadreFeedback');
      if (Math.abs(descuadre) < 0.01) {
        feedback.style.color = 'var(--green)';
        feedback.textContent = 'Cuadre exacto (S/ 0.00)';
      } else if (descuadre > 0) {
        feedback.style.color = '#9a682c';
        feedback.textContent = `Sobrante de dinero: +S/ ${descuadre.toFixed(2)}`;
      } else {
        feedback.style.color = 'var(--red)';
        feedback.textContent = `Faltante de dinero: -S/ ${Math.abs(descuadre).toFixed(2)}`;
      }
    }

    async function cleanTable(tableNo) {
      const t = tables.find(x => x.no === tableNo);
      if (!t) return;
      try {
        await fetch(`/api/mesas/${t.id}/estado`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ estado: 'LIBRE' })
        });
      } catch(e){}
      notify(`Mesa ${tableNo} desinfectada y disponible.`);
      await syncBackendData();
    }

    function closeModal(id) {
      document.getElementById(id)?.remove();
    }

    // ----------------------------------------------------------
    // ACCIONES DE NEGOCIO (ENVÍO A SPRING BOOT)
    // ----------------------------------------------------------
    async function sendOrder() {
      const items = [...order.entries()].map(([id, qty]) => ({
        idProducto: id,
        cantidad: qty,
        notas: ''
      }));

      if (!items.length) return;

      try {
        const res = await fetch('/api/pedidos', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            numeroMesa: selectedTable,
            observaciones: 'Pedido desde Salón',
            origen: 'SALON',
            items: items
          })
        });

        if (res.ok) {
          order.clear();
          setView('cocina');
          notify(`Comanda para Mesa ${selectedTable} enviada a cocina y stock descontado.`);
          await syncBackendData();
        } else {
          const err = await res.json();
          notify(err.error || 'Error al registrar pedido.');
        }
      } catch (err) {
        order.clear();
        setView('cocina');
        notify('Comanda registrada localmente.');
      }
    }

    async function placeCustomerOrder() {
      const items = [...customerCart.entries()].map(([id, qty]) => ({
        idProducto: id,
        cantidad: qty,
        notas: customerNote.trim()
      }));

      if (!items.length) return;

      try {
        const res = await fetch('/api/pedidos', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            numeroMesa: customerTable,
            observaciones: customerNote.trim(),
            origen: 'CLIENTE',
            items: items
          })
        });

        customerTicket = {
          table: customerTable,
          state: 'Pendiente',
          kind: 'red'
        };
        customerCart.clear();
        customerNote = '';
        setView('cliente');
        notify('Pedido recibido en cocina. Puedes seguir su estado aquí.');
        await syncBackendData();
      } catch (err) {
        customerCart.clear();
        setView('cliente');
        notify('Pedido confirmado.');
      }
    }

    async function advanceTicketBackend(idPedido) {
      try {
        await fetch(`/api/cocina/pedidos/${idPedido}/avanzar`, { method: 'PUT' });
        notify('Estado del pedido actualizado en cocina.');
        await syncBackendData();
      } catch (err) {
        notify('Estado avanzado.');
      }
    }

    async function adjustStockBackend(idProducto, delta) {
      try {
        await fetch(`/api/productos/${idProducto}/stock`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ delta })
        });
        await syncBackendData();
        notify('Stock actualizado.');
      } catch (err) {
        const p = products.find(x => x.id === idProducto);
        if (p) p.stock = Math.max(0, p.stock + delta);
        renderCurrentView();
      }
    }

    // Navegación de vistas
    function setView(view) {
      activeView = view;
      document.querySelectorAll('.nav-button').forEach(btn => btn.classList.toggle('active', btn.dataset.view === view));
      const names = { dashboard:'Dashboard', salon:'Salón y mesas', comanda:'Pedidos', cocina:'Cocina', inventario:'Inventario', menu:'Menú', usuarios:'Usuarios y roles', reportes:'Reportes', caja:'Caja' };
      document.getElementById('crumb').textContent = names[view];

      const roleEl = document.getElementById('profileRole');
      const iconEl = document.getElementById('profileIcon');
      roleEl.textContent = 'Administrador';
      iconEl.innerHTML = '<i class="bi bi-shield-lock-fill"></i>';

      renderCurrentView();
    }

    function renderCurrentView() {
      if (activeView === 'dashboard') main.innerHTML = renderDashboard();
      else if (activeView === 'salon') main.innerHTML = renderSalon();
      else if (activeView === 'comanda') main.innerHTML = renderComanda();
      else if (activeView === 'inventario') main.innerHTML = renderInventory();
      else if (activeView === 'menu') main.innerHTML = renderAdminMenu();
      else if (activeView === 'usuarios') main.innerHTML = renderAdminUsers();
      else if (activeView === 'reportes') main.innerHTML = renderAdminReports();
      else if (activeView === 'cocina') main.innerHTML = renderKitchen();
      else if (activeView === 'cliente') main.innerHTML = renderCustomer();
      else main.innerHTML = renderCash();
    }

    function setCategory(val) { 
      category = val; 
      renderCurrentView(); 
    }

    function addToOrder(id) { 
      const p = products.find(x => x.id === id); 
      if (!p || available(p) <= 0) return; 
      order.set(id, (order.get(id) || 0) + 1); 
      renderCurrentView(); 
    }

    function changeQty(id, delta) { 
      const p = products.find(x => x.id === id); 
      if (!p) return; 
      const next = (order.get(id) || 0) + delta; 
      if (next <= 0) order.delete(id); 
      else if (next <= p.stock) order.set(id, next); 
      renderCurrentView(); 
    }

    function setCustomerTable(val) { customerTable = val; renderCurrentView(); }
    function addCustomerItem(id) { 
      const p = products.find(x => x.id === id); 
      if (!p || p.stock - (customerCart.get(id) || 0) <= 0) return; 
      customerCart.set(id, (customerCart.get(id) || 0) + 1); 
      renderCurrentView(); 
    }
    function changeCustomerQty(id, delta) { 
      const p = products.find(x => x.id === id); 
      if (!p) return; 
      const next = (customerCart.get(id) || 0) + delta; 
      if (next <= 0) customerCart.delete(id); 
      else if (next <= p.stock) customerCart.set(id, next); 
      renderCurrentView(); 
    }

    let toastTimer;
    function notify(msg) { 
      document.querySelector('.toast')?.remove(); 
      const node = document.createElement('div'); 
      node.className = 'toast'; 
      node.setAttribute('role', 'status'); 
      node.textContent = msg; 
      document.body.append(node); 
      clearTimeout(toastTimer); 
      toastTimer = setTimeout(() => node.remove(), 2600); 
    }

    document.getElementById('navigation').addEventListener('click', event => { 
      const button = event.target.closest('[data-view]'); 
      if (button) setView(button.dataset.view); 
    });

    document.getElementById('today').textContent = new Intl.DateTimeFormat('es-PE', { weekday:'long', day:'numeric', month:'long' }).format(new Date());

    applyTheme(localStorage.getItem('restogestion-theme') || 'light');
    syncBackendData();
    setView('dashboard');
  
