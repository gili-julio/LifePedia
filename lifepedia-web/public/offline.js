// Página offline: volta sozinha quando a conexão voltar.
window.addEventListener('online', () => location.reload())
document.getElementById('retry').addEventListener('click', () => location.reload())
