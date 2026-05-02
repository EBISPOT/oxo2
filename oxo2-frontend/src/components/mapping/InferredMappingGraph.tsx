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

const minNodeWidth = 280;

const CHAIN_RULE_LABEL_HEIGHT = 18; // Height of the label extending below node
const MIN_NODE_HEIGHT = 60; // Minimum height for nodes with 3 lines of text
const NODE_LINE_HEIGHT = 18;
const NODE_VERTICAL_PADDING = 34;
const ESTIMATED_CHARS_PER_LINE = 34;
const CHAIN_RULE_CHAR_WIDTH = 7;
const CHAIN_RULE_HORIZONTAL_PADDING = 24;

function estimateNodeWidth(node: Node): number {
    if (node.type !== 'inferred' || !node.data.chainRule) {
        return minNodeWidth;
    }

    return Math.max(
        minNodeWidth,
        node.data.chainRule.length * CHAIN_RULE_CHAR_WIDTH + CHAIN_RULE_HORIZONTAL_PADDING
    );
}

function estimateWrappedLineCount(text: string): number {
    return text.split('\n').reduce((lineCount, line) => {
        return lineCount + Math.max(1, Math.ceil(line.length / ESTIMATED_CHARS_PER_LINE));
    }, 0);
}

function estimateNodeHeight(node: Node): number {
    const labelHeight = estimateWrappedLineCount(node.data.label) * NODE_LINE_HEIGHT;
    const chainRuleHeight = node.type === 'inferred' && node.data.chainRule
        ? CHAIN_RULE_LABEL_HEIGHT
        : 0;

    return Math.max(MIN_NODE_HEIGHT, labelHeight + NODE_VERTICAL_PADDING + chainRuleHeight);
}

const layoutElements = (nodes: Node[], edges: Edge[], direction = 'BT') => {
    // Create a new dagre graph instance each time
    const dagreGraph = new dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
    dagreGraph.setGraph({
        rankdir: direction, // Direction: BT (bottom-top), TB, LR, RL
        nodesep: 200,        // Space between nodes
        ranksep: 80,       // Space between ranks
        marginx: 20,        // Horizontal margin
        marginy: 20,         // Vertical margin
        edgesep: 50 
    });

    
    nodes.forEach((node) => {
        const width = estimateNodeWidth(node);
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
        const width = node.data.width ?? minNodeWidth;
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
                    : (current.chainRuleApplications?.chainRule?.chainRuleAbbreviated ?? '').replace(/[<>?]/g, '').replace(/ - /, ' ← ')
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
    return (
        <div className="custom-node-inferred" style={{ position: "relative", width: data.width }}>
            <div style={{ position: "relative", height: 0 }}>
                <Handle type="source" position={Position.Top} />
            </div>
            <div className="mapping-node-badge mapping-node-badge-inferred">Inferred</div>
            <MappingNodeContent data={data} />
            <div className="handle-label handle-label-bottom" >
                <Handle type="target" position={Position.Bottom}/>
                {data.chainRule && (
                    <div>{data.chainRule}</div>
                )}
            </div>
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
