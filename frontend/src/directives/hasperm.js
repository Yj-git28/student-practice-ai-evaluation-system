import { useUserStore } from '@/stores/useUserStore'

function apply(el, binding) {
  const perms = useUserStore().permissions || []
  const need = Array.isArray(binding.value) ? binding.value : [binding.value]

  const pass = binding.modifiers.all
    ? need.every(p => perms.includes(p))
    : need.some(p => perms.includes(p))

  if (pass) {
    // 此前被置灰的元素恢复（权限异步刷新后重新可见/可用）
    el.removeAttribute('disabled')
    el.classList.remove('is-disabled')
    el.style.pointerEvents = ''
    el.style.opacity = ''
    el.title = ''
    return
  }

  if (binding.modifiers.disable) {
    el.setAttribute('disabled', 'disabled')
    el.classList.add('is-disabled')
    el.style.pointerEvents = 'none'
    el.style.opacity = '0.5'
    el.title = '无操作权限'
  } else if (el.parentNode) {
    el.parentNode.removeChild(el)
  }
}

export default {
  mounted(el, binding) {
    apply(el, binding)
  },
  updated(el, binding) {
    // 权限异步刷新（如重新登录、角色变更）后重新判定显隐/置灰
    apply(el, binding)
  }
}
