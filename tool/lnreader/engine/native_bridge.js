// The sole JS -> Android transport. No plugin can supply a native operation
// directly without passing through the same permission and budget checks.
export function nativeCall(request) {
  const response = JSON.parse(__hikariHost(JSON.stringify(request)));
  if (response && typeof response === 'object' &&
      Object.hasOwn(response, '__hikariHostError')) {
    const reason = response.__hikariHostError;
    if (!['network-request-rejected', 'network-timeout', 'response-too-large',
           'unsupported-encoding'].includes(reason)) {
      throw new Error('Invalid native error response');
    }
    const error = new Error('LNReader host request rejected');
    error.hikariReason = reason;
    throw error;
  }
  return response;
}
