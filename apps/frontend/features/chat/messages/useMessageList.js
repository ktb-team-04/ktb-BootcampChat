export const deriveUniqueSortedMessages = (
  currentMessages,
  incomingMessages,
  processedMessageIds
) => {
  if (!Array.isArray(incomingMessages)) {
    throw new Error('Invalid messages format');
  }

  const processedSnapshot = new Set(processedMessageIds);
  const nextProcessedMessageIds = new Set(processedMessageIds);
  const newMessages = incomingMessages.filter((message) => {
    if (!message._id) {
      return false;
    }

    if (processedSnapshot.has(message._id)) {
      return false;
    }

    processedSnapshot.add(message._id);
    nextProcessedMessageIds.add(message._id);
    return true;
  });

  const toTime = (message) => {
    const timestamp = message.timestamp;
    if (typeof timestamp === 'number') return timestamp;
    return timestamp ? Date.parse(timestamp) || 0 : 0;
  };
  const sortedIncoming = newMessages.sort((a, b) => toTime(a) - toTime(b));
  const allMessages = [];
  let currentIndex = 0;
  let incomingIndex = 0;

  // currentMessages는 이 reducer가 정렬 상태를 보장한다. 두 정렬 배열을 선형 병합해
  // 페이지를 불러올 때마다 전체 목록을 다시 O(n log n) 정렬하지 않는다.
  while (currentIndex < currentMessages.length && incomingIndex < sortedIncoming.length) {
    if (toTime(currentMessages[currentIndex]) <= toTime(sortedIncoming[incomingIndex])) {
      allMessages.push(currentMessages[currentIndex++]);
    } else {
      allMessages.push(sortedIncoming[incomingIndex++]);
    }
  }

  allMessages.push(...currentMessages.slice(currentIndex));
  allMessages.push(...sortedIncoming.slice(incomingIndex));

  return {
    messages: allMessages,
    processedMessageIds: nextProcessedMessageIds,
  };
};

export const mergeUniqueSortedMessages = (
  currentMessages,
  incomingMessages,
  processedMessageIds
) => {
  return deriveUniqueSortedMessages(
    currentMessages,
    incomingMessages,
    processedMessageIds
  ).messages;
};
