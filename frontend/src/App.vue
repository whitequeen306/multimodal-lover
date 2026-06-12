<template>
  <div id="app-root">
    <router-view />
  </div>
</template>

<script setup>
// App root — router-view renders the matched page
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', Arial, sans-serif;
  background: linear-gradient(135deg, #fce4ec 0%, #f3e5f5 50%, #e8eaf6 100%);
  min-height: 100vh;
}

#app-root {
  min-height: 100vh;
}
</style>
