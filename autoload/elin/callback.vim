let s:registry = {}

function! elin#callback#register(callback) abort
  let id = sha256(string(a:callback))
  let s:registry[id] = a:callback
  return id
endfunction

function! elin#callback#unregister(id) abort
  if !has_key(s:registry, a:id)
    return
  endif
  silent unlet s:registry[a:id]
endfunction

function! elin#callback#call(id, ...) abort
  if !has_key(s:registry, a:id)
    throw printf('Callback id does not exists: %s', a:id)
  endif
  let ret = call(s:registry[a:id], a:000)
  call elin#callback#unregister(a:id)
  return ret
endfunction
