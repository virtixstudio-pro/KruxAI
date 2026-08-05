function addCodeToolbars() {
    const codeBlocks = document.querySelectorAll('pre');
    codeBlocks.forEach((pre) => {
        if (pre.querySelector('.code-toolbar')) return;

        const toolbar = document.createElement('div');
        toolbar.className = 'code-toolbar';
        toolbar.style.cssText = 'display: flex; justify-content: flex-end; gap: 8px; background: #1e1e2e; padding: 6px 12px; border-radius: 8px 8px 0 0; border-bottom: 1px solid #313244;';

        const btnCopy = document.createElement('button');
        btnCopy.innerText = '📋 Copier';
        btnCopy.onclick = () => {
            const code = pre.querySelector('code') ? pre.querySelector('code').innerText : pre.innerText;
            if (window.AndroidInterface) {
                window.AndroidInterface.copyToClipboard(code);
            }
        };

        const btnShare = document.createElement('button');
        btnShare.innerText = '📤 Partager';
        btnShare.onclick = () => {
            const code = pre.querySelector('code') ? pre.querySelector('code').innerText : pre.innerText;
            if (window.AndroidInterface) {
                window.AndroidInterface.shareCode(code);
            }
        };

        const btnDownload = document.createElement('button');
        btnDownload.innerText = '💾 Télécharger';
        btnDownload.onclick = () => {
            const code = pre.querySelector('code') ? pre.querySelector('code').innerText : pre.innerText;
            if (window.AndroidInterface) {
                window.AndroidInterface.downloadCode(code, 'krux_script.txt');
            }
        };

        [btnCopy, btnShare, btnDownload].forEach(btn => {
            btn.style.cssText = 'background: #313244; color: #cdd6f4; border: none; padding: 4px 8px; border-radius: 4px; font-size: 12px; cursor: pointer;';
            toolbar.appendChild(btn);
        });

        pre.parentNode.insertBefore(toolbar, pre);
    });
}
document.addEventListener("DOMContentLoaded", addCodeToolbars);
