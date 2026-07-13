import {
    ReactFlow,
    Handle,
    Position,
    MarkerType,
    Controls
} from '@xyflow/react';
import dagre from '@dagrejs/dagre';
import '@xyflow/react/dist/style.css';
import { InferredMapping } from "../../model/Mapping";


interface Node {
    id: string;
    data: {
        label: string;
        subject: string;
        predicate: string;
        object: string;
        chainRule: string;
        // Source mapping set of an asserted leaf (ADR-0010/0012): which set this premise came from.
        // Set only on asserted nodes; surfaces cross-set provenance in the explanation graph.
        mappingSet?: string;
        width?: number;
        height?: number;
    };
    position: {
        x: number;
        y: number;
    };
    type: string;
}

/**
 *  Note that edges do not have labels. ChainRule information is displayed on handles (connectors on nodes).
 */
interface Edge {
    id: string;
    source: string;
    target: string;
    type: string;
    animated: boolean;
    markerEnd: {
        type: MarkerType,
        width: number,
        height: number,
        color: string
    }
}

const edgeMarker  = {
    type: MarkerType.ArrowClosed,
    width: 20,
    height: 20,
    color: '#222'
}
const edgeType = 'straight';

interface GraphData {
    nodes: Node[];
    edges: Edge[];
}

type CoordinateExtent = [[number, number], [number, number]];

const NODE_WIDTH = 340;

const MIN_NODE_HEIGHT = 60; // Minimum height for nodes with 3 lines of text
const NODE_LINE_HEIGHT = 18;
const NODE_VERTICAL_PADDING = 34;
const ESTIMATED_CHARS_PER_LINE = 30;
// The chain-rule footer wraps inside the node (see CustomNodeInferred); estimate its
// rendered height from the wrapped line count so dagre reserves vertical space for it.
const RULE_CHARS_PER_LINE = 46;
const RULE_LINE_HEIGHT = 15;
const RULE_FOOTER_PADDING = 14; // separator margin + padding above the wrapped rule text
const RULE_LABEL_HEIGHT = 16; // the "Rule applied" heading above the wrapped rule text

// All nodes share a fixed width. The chain-rule text wraps inside the node rather than
// stretching it onto a single non-wrapping line, which previously produced very wide,
// lopsided nodes and a sprawling layout.
function estimateNodeWidth(): number {
    return NODE_WIDTH;
}

// Split a chain-rule string into display lines at top-level (depth-0) boundaries so that each
// parenthesised triple stays intact — a matching '(' and ')' always land on the same line, which
// makes the rule far easier to read than arbitrary browser wrapping. The implication arrow starts
// a new line (it introduces the rule body); a top-level comma (which separates body atoms) ends
// the line it closes. Example:
//   (a, P, c) ← (a, P, b), (b, P, c)
// becomes
//   (a, P, c)
//   ← (a, P, b),
//   (b, P, c)
function splitRuleIntoLines(rule: string): string[] {
    const lines: string[] = [];
    let current = '';
    let depth = 0;
    for (const char of rule) {
        if (char === '←' && depth === 0) {
            if (current.trim()) {
                lines.push(current.trim());
            }
            current = char;
            continue;
        }
        if (char === '(') {
            depth++;
        } else if (char === ')') {
            depth--;
        }
        current += char;
        if (char === ',' && depth === 0) {
            lines.push(current.trim());
            current = '';
        }
    }
    if (current.trim()) {
        lines.push(current.trim());
    }
    return lines;
}

function estimateRuleFooterHeight(node: Node): number {
    if (node.type !== 'inferred' || !node.data.chainRule) {
        return 0;
    }
    // Each rule part occupies its own line; a part longer than the node width wraps further.
    const visualLineCount = splitRuleIntoLines(node.data.chainRule).reduce((count, line) => {
        return count + Math.max(1, Math.ceil(line.length / RULE_CHARS_PER_LINE));
    }, 0);
    return RULE_FOOTER_PADDING + RULE_LABEL_HEIGHT + Math.max(1, visualLineCount) * RULE_LINE_HEIGHT;
}

function estimateSourceFooterHeight(node: Node): number {
    if (node.type !== 'asserted' || !node.data.mappingSet) {
        return 0;
    }
    // The full mapping-set IRI wraps inside the node (see CustomNodeAsserted); reserve space for the
    // "Source set" label plus the wrapped IRI so dagre sizes the node to fit it, matching the rule footer.
    const wrappedLineCount = Math.max(1, Math.ceil(node.data.mappingSet.length / RULE_CHARS_PER_LINE));
    return RULE_FOOTER_PADDING + RULE_LABEL_HEIGHT + wrappedLineCount * RULE_LINE_HEIGHT;
}

function estimateWrappedLineCount(text: string): number {
    return text.split('\n').reduce((lineCount, line) => {
        return lineCount + Math.max(1, Math.ceil(line.length / ESTIMATED_CHARS_PER_LINE));
    }, 0);
}

function estimateNodeHeight(node: Node): number {
    const labelHeight = estimateWrappedLineCount(node.data.label) * NODE_LINE_HEIGHT;
    const chainRuleHeight = estimateRuleFooterHeight(node);
    // Asserted nodes carry a labelled source-mapping-set footer that wraps the full IRI.
    const sourceFooterHeight = estimateSourceFooterHeight(node);

    return Math.max(MIN_NODE_HEIGHT, labelHeight + NODE_VERTICAL_PADDING + chainRuleHeight + sourceFooterHeight);
}

const layoutElements = (nodes: Node[], edges: Edge[], direction = 'BT') => {
    // Create a new dagre graph instance each time
    const dagreGraph = new dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
    dagreGraph.setGraph({
        rankdir: direction, // Direction: BT (bottom-top), TB, LR, RL
        nodesep: 70,         // Space between sibling nodes (uniform node width, so keep tight)
        ranksep: 90,         // Space between ranks
        marginx: 20,        // Horizontal margin
        marginy: 20,         // Vertical margin
        edgesep: 30
    });

    
    nodes.forEach((node) => {
        const width = estimateNodeWidth();
        const height = estimateNodeHeight(node);
        node.data.width = width;
        node.data.height = height;
        dagreGraph.setNode(node.id, { width, height });
    });

    edges.forEach((edge) => {
        dagreGraph.setEdge(edge.source, edge.target);
    });

    dagre.layout(dagreGraph);

    const newNodes = nodes.map((node) => {
        const nodeWithPosition = dagreGraph.node(node.id);
        const newNode = {
            ...node,
            position: {
                x: nodeWithPosition.x - nodeWithPosition.width / 2,
                y: nodeWithPosition.y - nodeWithPosition.height / 2,
            },
        };

        return newNode;
    });

    return { nodes: newNodes, edges };
};

function getGraphExtent(nodes: Node[], padding = 240): CoordinateExtent {
    if (nodes.length === 0) {
        return [[-padding, -padding], [padding, padding]];
    }

    const bounds = nodes.reduce((acc, node) => {
        const width = node.data.width ?? NODE_WIDTH;
        const height = node.data.height ?? MIN_NODE_HEIGHT;

        return {
            minX: Math.min(acc.minX, node.position.x),
            minY: Math.min(acc.minY, node.position.y),
            maxX: Math.max(acc.maxX, node.position.x + width),
            maxY: Math.max(acc.maxY, node.position.y + height),
        };
    }, {
        minX: Infinity,
        minY: Infinity,
        maxX: -Infinity,
        maxY: -Infinity,
    });

    return [
        [bounds.minX - padding, bounds.minY - padding],
        [bounds.maxX + padding, bounds.maxY + padding],
    ];
}


function createMappingId(mapping: InferredMapping): string {
    const data = `${mapping.objectIri}|${mapping.predicateIri}|${mapping.subjectIri}|${mapping.mappingSetId}`;
    return data;
}


function buildGraphData(mapping: InferredMapping): GraphData {
    const nodes: Node[] = [];
    const edges: Edge[] = [];
    const visited = new Set<string>();

    function traverse(current: InferredMapping) {
        const nodeId = createMappingId(current);
        if (visited.has(nodeId)) return;
        visited.add(nodeId);

        const isAsserted = current.chainRuleApplications?.chainRule?.chainRuleName === "Asserted";
        const formatEntity = (label?: string, idOrIri?: string) =>
            label ? `${label} (${idOrIri ?? ''})` : (idOrIri ?? '');
        const subject = formatEntity(current.subjectLabel, current.subjectId ?? current.subjectIri);
        const predicate = current.predicateId ?? current.predicateIri ?? '';
        const object = formatEntity(current.objectLabel, current.objectId ?? current.objectIri);
        nodes.push({
            id: nodeId,
            data: {
                label: [subject, predicate, object].join("\n"),
                subject,
                predicate,
                object,
                chainRule: isAsserted ? ''
                    : (current.chainRuleApplications?.chainRule?.chainRuleAbbreviated ?? '').replace(/[<>?]/g, '').replace(/ - /, ' ← '),
                mappingSet: isAsserted ? (current.mappingSetId ?? '') : ''
            },
            position: {
                x: 0,
                y: 0,
            },
            type: isAsserted ? "asserted" : "inferred"
        });

        const premises = current.chainRuleApplications?.premises;
        if (premises && premises.length > 0) {
            premises.forEach(premise => {
                const premiseId = createMappingId(premise);
                traverse(premise);

                edges.push({
                    id: `${premiseId}-${nodeId}`,
                    source: premiseId,
                    target: nodeId,
                    type: edgeType,
                    animated: false,
                    markerEnd: edgeMarker
                });
            });
        }
    }
    traverse(mapping);
    return { nodes, edges };
}

interface CustomNodeData {
    label: string;
    subject: string;
    predicate: string;
    object: string;
    chainRule?: string;
    mappingSet?: string;
    width?: number;
    height?: number;
}

function MappingNodeContent({ data }: { data: CustomNodeData }) {
    return (
        <div className="mapping-node-content">
            <div className="mapping-node-field mapping-node-entity-field">
                <span className="mapping-node-field-key">Subject</span>
                <span className="mapping-node-field-value">{data.subject}</span>
            </div>
            <div className="mapping-node-field mapping-node-predicate-field">
                <span className="mapping-node-field-key">Predicate</span>
                <span className="mapping-node-field-value mapping-node-predicate-value">{data.predicate}</span>
            </div>
            <div className="mapping-node-field mapping-node-entity-field">
                <span className="mapping-node-field-key">Object</span>
                <span className="mapping-node-field-value">{data.object}</span>
            </div>
        </div>
    );
}

function CustomNodeInferred({ data }: { data: CustomNodeData }) {
    // Distinguish the rule's head (conclusion) from its body (premises): the head is every line
    // before the implication arrow, the body is the arrow line and everything after it. The head
    // is emphasised, the body muted and indented, and the arrow itself enlarged/coloured.
    const ruleLines = data.chainRule ? splitRuleIntoLines(data.chainRule) : [];
    const bodyStartIndex = ruleLines.findIndex(line => line.startsWith('←'));
    return (
        <div className="custom-node-inferred" style={{ position: "relative", width: data.width }}>
            <Handle type="source" position={Position.Top} />
            <div className="mapping-node-badge mapping-node-badge-inferred">Inferred</div>
            <MappingNodeContent data={data} />
            {ruleLines.length > 0 && (
                <div className="mapping-node-rule">
                    <div className="mapping-node-rule-label">Rule applied</div>
                    {ruleLines.map((line, index) => {
                        const isBody = bodyStartIndex !== -1 && index >= bodyStartIndex;
                        const lineClass = isBody
                            ? "mapping-node-rule-line mapping-node-rule-body"
                            : "mapping-node-rule-line mapping-node-rule-head";
                        if (line.startsWith('←')) {
                            // Drop the arrow and the space after it: the arrow renders in its own
                            // hanging gutter (with margin-right for the gap), so the triple must
                            // start flush at the gutter to align with the premise lines below it.
                            return (
                                <div key={index} className={lineClass}>
                                    <span className="mapping-node-rule-arrow">←</span>{line.slice(1).trimStart()}
                                </div>
                            );
                        }
                        return <div key={index} className={lineClass}>{line}</div>;
                    })}
                </div>
            )}
            <Handle type="target" position={Position.Bottom} />
        </div>
    );
}

function CustomNodeAsserted({ data }: { data: CustomNodeData }) {
    return (
        <div className="custom-node-asserted" style={{ position: "relative", width: data.width }}>
            <div style={{ position: "relative", height: 0 }}>
                <Handle type="source" position={Position.Top} />
            </div>
            <div className="mapping-node-badge mapping-node-badge-asserted">Asserted</div>
            <MappingNodeContent data={data} />
            {data.mappingSet && (
                <div className="mapping-node-source">
                    <div className="mapping-node-source-label">Source mapping set</div>
                    <div className="mapping-node-source-value" title={data.mappingSet}>
                        {data.mappingSet}
                    </div>
                </div>
            )}
        </div>
    );
}

const nodeTypes = {
    "asserted": CustomNodeAsserted,
    "inferred": CustomNodeInferred,
};

const InferredMappingGraph = ({ explanation }: { explanation: InferredMapping }) => {

    const graphData = buildGraphData(explanation);
    const layoutGraph = layoutElements(graphData.nodes, graphData.edges);
    const graphExtent = getGraphExtent(layoutGraph.nodes);
    const canvasHeight = '400px';

    return (
        <div style={{ width: "100%", height: canvasHeight }}>
            <h1 className="section-subheading">Explanation of inferred mapping</h1>
            {layoutGraph && (
                    <ReactFlow
                        nodes={layoutGraph.nodes}
                        edges={layoutGraph.edges}
                        nodeTypes={nodeTypes}
                        fitView
                        fitViewOptions={{
                            padding: 0.2,
                            minZoom: 0.35,
                            maxZoom: 1.0
                        }}
                        translateExtent={graphExtent}
                        nodeExtent={graphExtent}
                        minZoom={0.35}
                        maxZoom={1.2}
                        panOnDrag={true}
                        panOnScroll={false}
                    >
                        <Controls showInteractive={false} />
                    </ReactFlow>
                )}
        </div>
    );
}

export default InferredMappingGraph;
