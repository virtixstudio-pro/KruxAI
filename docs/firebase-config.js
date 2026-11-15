// Configuration publique de l'app web Firebase (projet krux-9fd33).
// Firebase Console > Paramètres du projet > Vos applications > Application Web > "Configuration du SDK".
export const firebaseConfig = {
  apiKey: "AIzaSyAW5lL_bc1DDYZHNI6UbHScoiWI438O-v0",
  authDomain: "krux-9fd33.firebaseapp.com",
  projectId: "krux-9fd33",
  appId: "1:294133519242:web:e03bd88935d341a2c62e73"
};
export const configured = !String(firebaseConfig.apiKey).includes("REMPLACER");
